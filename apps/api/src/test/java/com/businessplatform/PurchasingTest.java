package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Retail purchasing: suppliers, ordering, deliveries (stock stays in VirtualBiz), payments and what is owed. */
class PurchasingTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

    @DynamicPropertySource
    static void virtualbiz(DynamicPropertyRegistry r) {
        r.add("platform.virtualbiz.url", () -> "https://shop.test/");
    }

    private record Setup(User owner, User member, String org) {

        String retail() {
            return "/api/orgs/" + org + "/retail";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Buy Owner");
        User member = signUp("Buy Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/retail", Map.of("enabled", true)));
        return new Setup(owner, member, org);
    }

    private static Map<String, Object> line(String description, String sku, String qty, String cost) {
        return new HashMap<>(Map.of("description", description, "sku", sku, "quantity", qty, "unitCost", cost));
    }

    @Test
    void an_order_is_placed_received_in_two_deliveries_and_paid() throws Exception {
        Setup s = setup();
        Map<String, Object> supplier = Map.of("name", "Phnom Penh Wholesale", "phone", "+855 12 345 678", "paymentTermsDays", 15);
        assertThat(code(call(s.member(), HttpMethod.POST, s.retail() + "/suppliers", supplier))).isEqualTo("FORBIDDEN");
        String sup = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/suppliers", supplier)).path("data").path("id").asText();

        // A draft: 10 × 3.50 + 4 × 12.00 = 83.00; nothing owed yet.
        JsonNode d = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders", Map.of("supplierId", sup,
                "lines", List.of(line("Coffee beans 1 kg", "CB-1KG", "10", "3.50"), line("Milk frother", "MF-01", "4", "12.00"))))).path("data");
        String po = d.path("summary").path("id").asText();
        assertThat(d.path("summary").path("total").decimalValue()).isEqualByComparingTo("83.00");
        assertThat(d.path("summary").path("number").isNull()).isTrue();
        assertThat(d.path("summary").path("balance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/receipts",
                Map.of("lines", List.of())))).isEqualTo("VALIDATION_FAILED");

        // Placed: it gets PO-0001 and is owed, due in 15 days.
        JsonNode placed = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/place", null)).path("data").path("summary");
        assertThat(placed.path("number").asText()).isEqualTo("PO-0001");
        assertThat(placed.path("dueDate").asText()).isEqualTo(TODAY.plusDays(15).toString());
        assertThat(placed.path("balance").decimalValue()).isEqualByComparingTo("83.00");
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.retail() + "/purchase-orders/" + po, Map.of("supplierId", sup,
                "lines", List.of(line("X", "X", "1", "1")))))).isEqualTo("INVALID_STATE");

        JsonNode view = ok(call(s.member(), HttpMethod.GET, s.retail() + "/purchase-orders/" + po, null)).path("data");
        assertThat(view.path("canReceive").asBoolean()).isFalse();
        String beans = view.path("lines").get(0).path("id").asText();
        String frother = view.path("lines").get(1).path("id").asText();

        // Two deliveries; never more than ordered.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/receipts",
                Map.of("lines", List.of(Map.of("lineId", beans, "quantity", "11")))))).isEqualTo("VALIDATION_FAILED");
        JsonNode part = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/receipts",
                Map.of("lines", List.of(Map.of("lineId", beans, "quantity", "6")), "note", "Short delivery"))).path("data");
        assertThat(part.path("summary").path("status").asText()).isEqualTo("PARTIAL");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/cancel", Map.of("reason", "x"))))
                .isEqualTo("INVALID_STATE");
        JsonNode all = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/receipts",
                Map.of("lines", List.of(Map.of("lineId", beans, "quantity", "4"), Map.of("lineId", frother, "quantity", "4"))))).path("data");
        assertThat(all.path("summary").path("status").asText()).isEqualTo("RECEIVED");
        assertThat(all.path("receipts")).hasSize(2);

        // Deliveries wait to be put into VirtualBiz stock until ticked off.
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.retail() + "/purchasing", null)).path("data").path("notInStock").asInt()).isEqualTo(2);
        ok(call(s.owner(), HttpMethod.PUT, s.retail() + "/receipts/" + all.path("receipts").get(0).path("id").asText() + "/stock-recorded",
                Map.of("recorded", true)));
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.retail() + "/purchasing", null)).path("data").path("notInStock").asInt()).isEqualTo(1);

        // Payments, never more than the total.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/payments",
                Map.of("paidOn", TODAY.toString(), "amount", "90.00", "method", "CASH")))).isEqualTo("VALIDATION_FAILED");
        ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/payments",
                Map.of("paidOn", TODAY.toString(), "amount", "50.00", "method", "BANK_TRANSFER", "reference", "ABA 123")));
        JsonNode o = ok(call(s.owner(), HttpMethod.GET, s.retail() + "/purchasing", null)).path("data");
        assertThat(o.path("owed").decimalValue()).isEqualByComparingTo("33.00");
        assertThat(o.path("spendThisYear").get(0).path("supplierName").asText()).isEqualTo("Phnom Penh Wholesale");
        JsonNode sup2 = ok(call(s.member(), HttpMethod.GET, s.retail() + "/suppliers/" + sup, null)).path("data");
        assertThat(sup2.path("owed").decimalValue()).isEqualByComparingTo("33.00");
        JsonNode log = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=retail", null)).path("data");
        assertThat(log.findValuesAsText("verb")).contains("retail.po.ordered", "retail.po.part_received", "retail.po.received", "retail.po.paid");
    }

    @Test
    void an_order_nothing_arrived_for_can_be_cancelled_and_a_draft_deleted() throws Exception {
        Setup s = setup();
        String sup = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/suppliers", Map.of("name", "Siem Reap Paper", "paymentTermsDays", 0)))
                .path("data").path("id").asText();
        String draft = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders",
                Map.of("supplierId", sup, "lines", List.of(line("Bags", "BAG", "100", "0.10"))))).path("data").path("summary").path("id").asText();
        ok(call(s.owner(), HttpMethod.DELETE, s.retail() + "/purchase-orders/" + draft, null));
        String po = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders",
                Map.of("supplierId", sup, "lines", List.of(line("Cups", "CUP", "200", "0.05"))))).path("data").path("summary").path("id").asText();
        ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/place", null));
        assertThat(code(call(s.owner(), HttpMethod.DELETE, s.retail() + "/purchase-orders/" + po, null))).isEqualTo("INVALID_STATE");
        JsonNode c = ok(call(s.owner(), HttpMethod.POST, s.retail() + "/purchase-orders/" + po + "/cancel",
                Map.of("reason", "Found a cheaper supplier"))).path("data");
        assertThat(c.path("summary").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(c.path("summary").path("balance").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.retail() + "/purchasing", null)).path("data").path("owed").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void general_business_alone_can_buy_and_nothing_without_it_or_retail() throws Exception {
        User owner = signUp("Biz Buyer");
        String org = newOrg(owner);
        String base = "/api/orgs/" + org + "/retail";
        assertThat(code(call(owner, HttpMethod.GET, base + "/suppliers", null))).isEqualTo("MODULE_DISABLED");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        String sup = ok(call(owner, HttpMethod.POST, base + "/suppliers", Map.of("name", "Phnom Penh Office Supply", "paymentTermsDays", 30)))
                .path("data").path("id").asText();
        assertThat(sup).isNotBlank();
        assertThat(ok(call(owner, HttpMethod.GET, base + "/suppliers", null)).path("data").toString()).contains("Phnom Penh Office Supply");
    }
}
