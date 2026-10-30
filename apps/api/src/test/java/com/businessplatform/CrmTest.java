package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** General Business: the sales pipeline and the finance reports. */
class CrmTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

    private record Setup(User owner, User seller, User colleague, String org) {

        String biz() {
            return "/api/orgs/" + org + "/business";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Crm Owner");
        User seller = signUp("Crm Seller");
        User colleague = signUp("Crm Colleague");
        String org = newOrg(owner);
        join(owner, org, seller, "MEMBER");
        join(owner, org, colleague, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        return new Setup(owner, seller, colleague, org);
    }

    private static Map<String, Object> deal(String title, String company, String value) {
        Map<String, Object> d = new HashMap<>(Map.of("title", title, "value", value, "source", "REFERRAL"));
        if (company != null) {
            d.put("company", company);
            d.put("contactName", "Mr. Sok");
            d.put("email", "sok@example.com");
        }
        return d;
    }

    @Test
    void a_lead_moves_through_the_pipeline_becomes_a_customer_with_a_quote_and_is_won() throws Exception {
        Setup s = setup();
        assertThat(code(call(s.seller(), HttpMethod.POST, s.biz() + "/deals", deal("No one", null, "100")))).isEqualTo("VALIDATION_FAILED");
        JsonNode d = ok(call(s.seller(), HttpMethod.POST, s.biz() + "/deals", deal("Office fit-out", "Mekong Coffee", "1200"))).path("data").path("deal");
        String id = d.path("id").asText();
        assertThat(d.path("stage").asText()).isEqualTo("LEAD");
        assertThat(d.path("owner").path("id").asText()).isEqualTo(s.seller().id().toString());
        ok(call(s.seller(), HttpMethod.POST, s.biz() + "/deals", deal("Coffee machines", "Riverside Hotel", "800")));

        // Lead 10 %: weighted 120 + 80.
        JsonNode p = ok(call(s.colleague(), HttpMethod.GET, s.biz() + "/deals", null)).path("data");
        assertThat(p.path("openValue").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(p.path("weighted").decimalValue()).isEqualByComparingTo("200.00");

        // A colleague logs a call but can't change someone else's deal.
        ok(call(s.colleague(), HttpMethod.POST, s.biz() + "/deals/" + id + "/activities", Map.of("kind", "CALL", "body", "Asked for a site visit")));
        assertThat(code(call(s.colleague(), HttpMethod.POST, s.biz() + "/deals/" + id + "/stage", Map.of("stage", "QUALIFIED"))))
                .isEqualTo("FORBIDDEN");
        ok(call(s.seller(), HttpMethod.POST, s.biz() + "/deals/" + id + "/stage", Map.of("stage", "QUALIFIED")));

        // Starting a quote makes the lead a customer, links the quote and moves the deal to Proposal.
        JsonNode q = ok(call(s.seller(), HttpMethod.POST, s.biz() + "/deals/" + id + "/quote", null)).path("data");
        assertThat(q.path("customer").path("name").asText()).isEqualTo("Mekong Coffee");
        assertThat(q.path("total").decimalValue()).isEqualByComparingTo(q.path("subtotal").decimalValue().add(q.path("vatTotal").decimalValue()));
        JsonNode after = ok(call(s.seller(), HttpMethod.GET, s.biz() + "/deals/" + id, null)).path("data");
        assertThat(after.path("deal").path("stage").asText()).isEqualTo("PROPOSAL");
        assertThat(after.path("deal").path("customer").path("name").asText()).isEqualTo("Mekong Coffee");
        assertThat(after.path("deal").path("quote").path("number").asText()).startsWith("Q-");
        assertThat(after.path("activities").get(0).path("body").asText()).isEqualTo("Asked for a site visit");

        // Lost needs a reason; won closes it.
        assertThat(code(call(s.seller(), HttpMethod.POST, s.biz() + "/deals/" + id + "/stage", Map.of("stage", "LOST"))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(s.seller(), HttpMethod.POST, s.biz() + "/deals/" + id + "/stage", Map.of("stage", "WON")));
        JsonNode won = ok(call(s.owner(), HttpMethod.GET, s.biz() + "/deals", null)).path("data");
        assertThat(won.path("wonThisMonth").decimalValue()).isEqualByComparingTo("1200.00");
        assertThat(won.path("winRate").asInt()).isEqualTo(100);
        JsonNode log = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=business", null)).path("data");
        assertThat(log.findValuesAsText("verb")).contains("business.deal.created", "business.deal.converted", "business.deal.quoted", "business.deal.won");
    }

    @Test
    void the_finance_report_shows_sales_collections_and_what_is_owed_for_admins_only() throws Exception {
        Setup s = setup();
        String customer = ok(call(s.owner(), HttpMethod.POST, s.biz() + "/customers", Map.of("kind", "COMPANY", "name", "Angkor Mart")))
                .path("data").path("id").asText();
        Map<String, Object> line = new HashMap<>(Map.of("description", "Supplies", "quantity", "1", "unitPrice", "500.00", "vat", false));
        String inv = ok(call(s.owner(), HttpMethod.POST, s.biz() + "/invoices", Map.of("customerId", customer, "lines", List.of(line))))
                .path("data").path("id").asText();
        ok(call(s.owner(), HttpMethod.POST, s.biz() + "/invoices/" + inv + "/issue", null));
        ok(call(s.owner(), HttpMethod.POST, s.biz() + "/invoices/" + inv + "/payments",
                Map.of("paidOn", TODAY.toString(), "amount", "200.00", "method", "CASH")));

        assertThat(code(call(s.seller(), HttpMethod.GET, s.biz() + "/reports?year=" + TODAY.getYear(), null))).isEqualTo("FORBIDDEN");
        JsonNode r = ok(call(s.owner(), HttpMethod.GET, s.biz() + "/reports?year=" + TODAY.getYear(), null)).path("data");
        JsonNode month = r.path("months").get(TODAY.getMonthValue() - 1);
        assertThat(month.path("sales").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(month.path("collected").decimalValue()).isEqualByComparingTo("200.00");
        assertThat(r.path("totals").path("profit").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(r.path("aging").path("total").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(r.path("agingByCustomer").get(0).path("customer").asText()).isEqualTo("Angkor Mart");
        assertThat(r.path("customers").get(0).path("invoiced").decimalValue()).isEqualByComparingTo("500.00");

        var csv = mvc.perform(MockMvcRequestBuilders.get(s.biz() + "/reports/aging.csv?year=" + TODAY.getYear())
                .header("Authorization", "Bearer " + s.owner().token())).andReturn().getResponse();
        assertThat(csv.getContentAsString(StandardCharsets.UTF_8)).contains("Angkor Mart").contains("300.00");
    }
}
