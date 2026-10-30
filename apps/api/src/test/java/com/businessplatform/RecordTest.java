package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Employee records: personal details, contracts, documents, salary history and what expires soon. */
class RecordTest extends IntegrationTest {

    private JsonNode upload(User u, String path, String name, Map<String, String> params) throws Exception {
        var req = MockMvcRequestBuilders.multipart(path)
                .file(new MockMultipartFile("file", name, "application/pdf", "%PDF-1.4 test".getBytes()))
                .header("Authorization", "Bearer " + u.token());
        params.forEach(req::param);
        return json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString());
    }

    private int status(User u, String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + u.token())).andReturn().getResponse().getStatus();
    }

    @Test
    void records_are_kept_private_to_the_person_and_hr() throws Exception {
        User owner = signUp("Rec Owner");
        User employee = signUp("Rec Employee");
        User colleague = signUp("Rec Colleague");
        String org = newOrg(owner);
        join(owner, org, employee, "MEMBER");
        join(owner, org, colleague, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String hr = "/api/orgs/" + org + "/hr";
        String rec = hr + "/people/" + employee.id();
        LocalDate today = LocalDate.now(CalendarService.ZONE);

        // Personal details: HR fills them in; the person changes only their own contacts.
        Map<String, Object> personal = new HashMap<>(Map.of("nickname", "Dara", "duty", "Mobile Developing", "category", "Software Engineer",
                "gender", "MALE", "dateOfBirth", "1995-04-02", "idType", "PASSPORT", "idNumber", "N1234567",
                "idExpiresOn", today.plusDays(20).toString(), "nssfNumber", "NSSF-998"));
        ok(call(owner, HttpMethod.PUT, rec + "/personal", personal));
        Map<String, Object> mine = new HashMap<>(Map.of("homeAddress", "St. 271, Phnom Penh", "personalPhone", "+855 12 345 678",
                "idNumber", "HACKED"));
        JsonNode r = ok(call(employee, HttpMethod.PUT, rec + "/personal", mine)).path("data");
        assertThat(r.path("personal").path("idNumber").asText()).isEqualTo("N1234567");
        assertThat(r.path("personal").path("homeAddress").asText()).isEqualTo("St. 271, Phnom Penh");
        assertThat(code(call(owner, HttpMethod.PUT, rec + "/personal", Map.of("dateOfBirth", today.plusDays(1).toString()))))
                .isEqualTo("VALIDATION_FAILED");
        // A colleague sees the nickname and duty, nothing private.
        JsonNode seen = ok(call(colleague, HttpMethod.GET, rec + "/records", null)).path("data");
        assertThat(seen.path("personal").path("nickname").asText()).isEqualTo("Dara");
        assertThat(seen.path("personal").path("idNumber").isNull()).isTrue();
        assertThat(seen.path("shown").asBoolean()).isFalse();
        assertThat(code(call(colleague, HttpMethod.PUT, rec + "/personal", mine))).isEqualTo("FORBIDDEN");

        // Contracts: a fixed-term one needs its end; the signed file only for the person and HR.
        Map<String, Object> fdc = new HashMap<>(Map.of("kind", "FIXED_TERM", "startsOn", today.minusMonths(11).toString(),
                "probationEndsOn", today.minusMonths(8).toString(), "monthlySalary", "750.00"));
        assertThat(code(call(owner, HttpMethod.POST, rec + "/contracts", fdc))).isEqualTo("VALIDATION_FAILED");
        fdc.put("endsOn", today.plusDays(25).toString());
        JsonNode c = ok(call(owner, HttpMethod.POST, rec + "/contracts", fdc)).path("data").path("contracts").get(0);
        assertThat(c.path("status").asText()).isEqualTo("ACTIVE");
        String contract = c.path("id").asText();
        assertThat(upload(owner, hr + "/contracts/" + contract + "/file", "contract.pdf", Map.of()).path("data").path("contracts").get(0)
                .path("fileName").asText()).isEqualTo("contract.pdf");
        assertThat(status(employee, hr + "/contracts/" + contract + "/file")).isEqualTo(200);
        assertThat(status(colleague, hr + "/contracts/" + contract + "/file")).isEqualTo(404);
        assertThat(code(call(employee, HttpMethod.POST, rec + "/contracts", fdc))).isEqualTo("FORBIDDEN");

        // Documents: people file their own certificates, not IDs; HR files the ID scan.
        assertThat(upload(employee, rec + "/documents", "passport.pdf", Map.of("category", "ID", "title", "Passport")).path("status")
                .path("code").asText()).isEqualTo("VALIDATION_FAILED");
        JsonNode cert = upload(employee, rec + "/documents", "aws.pdf", Map.of("category", "CERTIFICATE", "title", "AWS certificate"))
                .path("data").path("documents").get(0);
        assertThat(cert.path("canDelete").asBoolean()).isTrue();
        JsonNode id = upload(owner, rec + "/documents", "passport.pdf", Map.of("category", "ID", "title", "Passport scan",
                "expiresOn", today.plusDays(20).toString())).path("data").path("documents").get(0);
        assertThat(status(colleague, hr + "/documents/" + id.path("id").asText() + "/file")).isEqualTo(404);
        assertThat(code(call(employee, HttpMethod.DELETE, hr + "/documents/" + id.path("id").asText(), null))).isEqualTo("FORBIDDEN");
        ok(call(employee, HttpMethod.DELETE, hr + "/documents/" + cert.path("id").asText(), null));

        // What expires soon: the contract, the passport and its scan — for HR only.
        List<String> kinds = ok(call(owner, HttpMethod.GET, hr + "/records/expiring?days=30", null)).path("data").findValuesAsText("kind");
        assertThat(kinds).contains("CONTRACT_END", "ID_EXPIRY", "DOCUMENT_EXPIRY");
        assertThat(code(call(employee, HttpMethod.GET, hr + "/records/expiring", null))).isEqualTo("FORBIDDEN");

        // Salary history: each change, newest first, with the reason.
        String pay = hr + "/payroll/people/" + employee.id();
        ok(call(owner, HttpMethod.PUT, pay, Map.of("monthlySalary", "750.00", "dependants", 0, "nssfEnrolled", true)));
        ok(call(owner, HttpMethod.PUT, pay, Map.of("monthlySalary", "850.00", "dependants", 1, "nssfEnrolled", true, "reason", "Promotion")));
        ok(call(owner, HttpMethod.PUT, pay, Map.of("monthlySalary", "850.00", "dependants", 2, "nssfEnrolled", true)));
        JsonNode history = ok(call(owner, HttpMethod.GET, pay + "/history", null)).path("data");
        assertThat(history).hasSize(2);
        assertThat(history.get(0).path("monthlySalary").decimalValue()).isEqualByComparingTo("850.00");
        assertThat(history.get(0).path("previous").decimalValue()).isEqualByComparingTo("750.00");
        assertThat(history.get(0).path("reason").asText()).isEqualTo("Promotion");
        assertThat(code(call(employee, HttpMethod.GET, pay + "/history", null))).isEqualTo("FORBIDDEN");
    }
}
