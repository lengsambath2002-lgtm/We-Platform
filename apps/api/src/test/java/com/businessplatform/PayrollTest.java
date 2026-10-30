package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Payroll: settings, pay set-up, a month's run from draft to paid, and who sees what. */
class PayrollTest extends IntegrationTest {

    private record Setup(User owner, User employee, String org) {

        String pay() {
            return "/api/orgs/" + org + "/hr/payroll";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Pay Owner");
        User employee = signUp("Pay Employee");
        String org = newOrg(owner);
        join(owner, org, employee, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        Setup s = new Setup(owner, employee, org);
        ok(call(owner, HttpMethod.PUT, s.pay() + "/people/" + employee.id(), Map.of("monthlySalary", "600.00",
                "dependants", 1, "nssfEnrolled", true, "bankName", "ABA", "bankAccount", "000 111 222")));
        return s;
    }

    private static String lastMonth() {
        return YearMonth.now(CalendarService.ZONE).minusMonths(1).toString();
    }

    @Test
    void payroll_is_for_hr_managers_only() throws Exception {
        Setup s = setup();
        for (String path : List.of("/settings", "/people", "/runs")) {
            assertThat(code(call(s.employee(), HttpMethod.GET, s.pay() + path, null))).as(path).isEqualTo("FORBIDDEN");
        }
        assertThat(code(call(s.employee(), HttpMethod.PUT, s.pay() + "/people/" + s.employee().id(),
                Map.of("monthlySalary", "9000", "dependants", 0, "nssfEnrolled", false)))).isEqualTo("FORBIDDEN");
        // Salaries stay out of the audit log, which more people may read.
        JsonNode log = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null)).path("data");
        assertThat(log.toString()).contains("employee.pay.set").doesNotContain("600");
    }

    @Test
    void the_settings_start_with_cambodias_rates_and_are_checked() throws Exception {
        Setup s = setup();
        JsonNode st = ok(call(s.owner(), HttpMethod.GET, s.pay() + "/settings", null)).path("data");
        assertThat(st.path("taxBrackets")).hasSize(5);
        assertThat(st.path("taxBrackets").get(4).path("upToKhr").isNull()).isTrue();
        assertThat(st.path("dependantAllowanceKhr").asInt()).isEqualTo(150000);
        Map<String, Object> bad = new HashMap<>(Map.of("taxBrackets", List.of(Map.of("ratePct", 0), Map.of("upToKhr", 2000000, "ratePct", 5)),
                "dependantAllowanceKhr", 150000, "nssfFloorKhr", 400000, "nssfCapKhr", 1200000, "nssfPensionEmployeePct", 2,
                "nssfPensionEmployerPct", 2, "nssfHealthEmployerPct", 2.6, "nssfRiskEmployerPct", 0.8, "overtimeRatePct", 150));
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.pay() + "/settings", bad))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void a_month_goes_from_draft_to_paid_and_only_then_the_employee_sees_it() throws Exception {
        Setup s = setup();
        String month = lastMonth();
        JsonNode d = ok(call(s.owner(), HttpMethod.POST, s.pay() + "/runs", Map.of("month", month, "exchangeRate", 4100))).path("data");
        String run = d.path("run").path("id").asText();
        assertThat(d.path("run").path("status").asText()).isEqualTo("DRAFT");
        assertThat(d.path("payslips")).hasSize(1);
        assertThat(d.path("notSetUp").findValuesAsText("name")).containsExactly("Pay Owner");
        JsonNode slip = d.path("payslips").get(0);
        // The worked example in PayrollCalculatorTest: $600, one dependant, NSSF, 4,100 ៛.
        assertThat(slip.path("taxKhr").asLong()).isEqualTo(56000);
        assertThat(slip.path("net").decimalValue()).isEqualByComparingTo("580.49");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.pay() + "/runs", Map.of("month", month, "exchangeRate", 4100))))
                .isEqualTo("CONFLICT");

        // HR adds a transport allowance; the payslip is recalculated.
        String slipId = slip.path("id").asText();
        JsonNode adj = ok(call(s.owner(), HttpMethod.PUT, s.pay() + "/payslips/" + slipId + "/adjustments",
                Map.of("adjustments", List.of(Map.of("label", "Transport", "amount", "20.00"))))).path("data");
        assertThat(adj.path("payslips").get(0).path("gross").decimalValue()).isEqualByComparingTo("620.00");

        // Nothing for the employee yet.
        assertThat(code(call(s.employee(), HttpMethod.GET, s.pay() + "/payslips/" + slipId, null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.pay() + "/payslips/mine", null)).path("data")).isEmpty();

        // Approving locks it; reopening unlocks it; paying needs approval first.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.pay() + "/runs/" + run + "/pay", null))).isEqualTo("INVALID_STATE");
        ok(call(s.owner(), HttpMethod.POST, s.pay() + "/runs/" + run + "/approve", null));
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.pay() + "/payslips/" + slipId + "/adjustments",
                Map.of("adjustments", List.of())))).isEqualTo("INVALID_STATE");
        ok(call(s.owner(), HttpMethod.POST, s.pay() + "/runs/" + run + "/reopen", null));
        ok(call(s.owner(), HttpMethod.POST, s.pay() + "/runs/" + run + "/approve", null));
        JsonNode paid = ok(call(s.owner(), HttpMethod.POST, s.pay() + "/runs/" + run + "/pay", null)).path("data").path("run");
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("totals").path("people").asInt()).isEqualTo(1);
        assertThat(code(call(s.owner(), HttpMethod.DELETE, s.pay() + "/runs/" + run, null))).isEqualTo("INVALID_STATE");

        // Now the employee sees it, is notified and emailed.
        JsonNode mine = ok(call(s.employee(), HttpMethod.GET, s.pay() + "/payslips/mine", null)).path("data");
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).path("adjustments").get(0).path("label").asText()).isEqualTo("Transport");
        ok(call(s.employee(), HttpMethod.GET, s.pay() + "/payslips/" + slipId, null));
        // Their first email was the invitation; the second is the payslip.
        assertThat(mails.await(s.employee().email(), 2).text()).contains("/hr/payslips/" + slipId);
        assertThat(ok(call(s.employee(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).toString())
                .contains("payroll.paid");

        // The bank transfer list.
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get(s.pay() + "/runs/" + run + "/export.csv").header("Authorization", "Bearer " + s.owner().token()))
                .andReturn().getResponse();
        assertThat(res.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("Pay Employee").contains("ABA")
                .contains("000 111 222");
    }
}
