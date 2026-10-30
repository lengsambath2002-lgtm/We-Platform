package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.businessplatform.billing.khqr.KhqrBuilder;
import com.businessplatform.billing.service.PlatformInvoiceService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Invoices: issued with a paid plan (with KHQR), claimed, confirmed, late, unpaid, renewed. */
class InvoiceTest extends IntegrationTest {

    @DynamicPropertySource
    static void khqr(DynamicPropertyRegistry r) {
        r.add("platform.billing.bakong-account-id", () -> "platform@aclb");
        r.add("platform.billing.bank-name", () -> "ABA Bank");
        r.add("platform.billing.bank-account-number", () -> "000 111 222");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformInvoiceService invoices;

    private String orgId(User u, String org) throws Exception {
        return ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("org").path("id").asText();
    }

    @Test
    void a_paid_plan_is_invoiced_paid_and_renewed() throws Exception {
        User owner = signUp("Inv Owner");
        User member = signUp("Inv Member");
        User root = signUp("Inv Root");
        jdbc.update("UPDATE app_user SET super_admin = true WHERE id = ?", root.id());
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String api = "/api/orgs/" + org;
        ok(call(owner, HttpMethod.PUT, api + "/subscription", Map.of("planCode", "STANDARD", "cycle", "MONTHLY")));

        // Two members × $3: an open invoice with a valid KHQR and the bank details; the owner is emailed.
        JsonNode inv = ok(call(owner, HttpMethod.GET, api + "/invoices", null)).path("data").get(0);
        assertThat(inv.path("status").asText()).isEqualTo("OPEN");
        assertThat(inv.path("amount").decimalValue()).isEqualByComparingTo("6.00");
        assertThat(inv.path("number").asText()).startsWith("INV-");
        assertThat(KhqrBuilder.crcValid(inv.path("khqr").asText())).isTrue();
        assertThat(inv.path("khqr").asText()).contains("platform@aclb").contains("6.00");
        assertThat(inv.path("payTo").path("bankName").asText()).isEqualTo("ABA Bank");
        assertThat(mails.await(owner.email(), 1).subject()).startsWith("Invoice INV-");
        String id = inv.path("id").asText();

        // The owner says they paid; members can't see invoices; the platform confirms.
        assertThat(code(call(member, HttpMethod.GET, api + "/invoices", null))).isEqualTo("FORBIDDEN");
        ok(call(owner, HttpMethod.POST, api + "/invoices/" + id + "/claim", Map.of("reference", "ABA-778899")));
        assertThat(mails.await(root.email(), 1).text()).contains("ABA-778899");
        assertThat(code(call(owner, HttpMethod.POST, "/api/platform/invoices/" + id + "/payment", Map.of("method", "KHQR")))).isEqualTo("NOT_FOUND");
        JsonNode paid = ok(call(root, HttpMethod.POST, "/api/platform/invoices/" + id + "/payment", Map.of("method", "BANK_TRANSFER"))).path("data");
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(paid.path("paymentReference").asText()).isEqualTo("ABA-778899");
        assertThat(mails.await(owner.email(), 2).subject()).startsWith("Payment received");

        // The period ends: renewed with a new invoice; left unpaid it is late, then the company moves to Free.
        jdbc.update("UPDATE subscription SET current_period_end = now() - interval '1 hour' WHERE org_id = ?::uuid", orgId(owner, org));
        invoices.run();
        List<JsonNode> all = new java.util.ArrayList<>();
        ok(call(owner, HttpMethod.GET, api + "/invoices", null)).path("data").forEach(all::add);
        assertThat(all).hasSize(2);
        assertThat(all.get(0).path("status").asText()).isEqualTo("OPEN");
        String next = all.get(0).path("id").asText();
        jdbc.update("UPDATE billing_invoice SET due_at = now() - interval '1 day' WHERE id = ?::uuid", next);
        invoices.run();
        assertThat(ok(call(owner, HttpMethod.GET, api + "/subscription", null)).path("data").path("status").asText()).isEqualTo("PAST_DUE");
        jdbc.update("UPDATE billing_invoice SET due_at = now() - interval '30 days' WHERE id = ?::uuid", next);
        invoices.run();
        JsonNode sub = ok(call(owner, HttpMethod.GET, api + "/subscription", null)).path("data");
        assertThat(sub.path("plan").path("code").asText()).isEqualTo("FREE");
        assertThat(ok(call(owner, HttpMethod.GET, api + "/invoices/" + next, null)).path("data").path("status").asText()).isEqualTo("VOID");
    }
}
