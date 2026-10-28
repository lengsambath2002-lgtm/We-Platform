package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * General Business: module switch and permissions, server-side totals (rounding, discounts, VAT), invoice
 * numbering under concurrency, payments, voiding, quote conversion, expenses through the approval engine and
 * isolation between organisations.
 */
class BusinessTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Phnom_Penh"));

    /** Owner and Admin run the business; two Members sell; a Guest. Business switched on. */
    private record Setup(User owner, User admin, User member, User other, User guest, String org) {

        String biz() {
            return "/api/orgs/" + org + "/business";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Biz Owner");
        User admin = signUp("Biz Admin");
        User member = signUp("Sok Seller");
        User other = signUp("Dara Seller");
        User guest = signUp("Biz Guest");
        String org = newOrg(owner);
        join(owner, org, admin, "ADMIN");
        join(owner, org, member, "MEMBER");
        join(owner, org, other, "MEMBER");
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        return new Setup(owner, admin, member, other, guest, org);
    }

    private String customer(User u, Setup s, String name) throws Exception {
        return ok(call(u, HttpMethod.POST, s.biz() + "/customers", Map.of("kind", "COMPANY", "name", name,
                "email", "billing@example.com", "taxId", "K001-123456789"))).path("data").path("id").asText();
    }

    private static Map<String, Object> line(String description, String qty, String price, String discount, boolean vat) {
        Map<String, Object> l = new HashMap<>();
        l.put("description", description);
        l.put("quantity", qty);
        l.put("unitPrice", price);
        l.put("discountPct", discount);
        l.put("vat", vat);
        return l;
    }

    private JsonNode draft(User u, Setup s, String customerId, List<Map<String, Object>> lines) throws Exception {
        return call(u, HttpMethod.POST, s.biz() + "/invoices", Map.of("customerId", customerId, "lines", lines));
    }

    private String simpleDraft(User u, Setup s, String customerId, String price) throws Exception {
        return ok(draft(u, s, customerId, List.of(line("Consulting", "1", price, "0", false)))).path("data").path("id").asText();
    }

    private static String dec(JsonNode n) {
        return n.decimalValue().setScale(2).toPlainString();
    }

    private List<String> permissions(User u, String org) throws Exception {
        List<String> out = new ArrayList<>();
        ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions")
                .forEach(p -> out.add(p.asText()));
        return out;
    }

    @Test
    void the_module_is_off_until_an_admin_switches_it_on() throws Exception {
        User owner = signUp("Off Owner");
        User member = signUp("Off Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String biz = "/api/orgs/" + org + "/business";
        for (String path : List.of("/overview", "/customers", "/products", "/quotes", "/invoices", "/expenses", "/settings")) {
            assertThat(code(call(owner, HttpMethod.GET, biz + path, null))).as(path).isEqualTo("MODULE_DISABLED");
        }
        assertThat(permissions(owner, org)).noneMatch(p -> p.startsWith("business."));
        assertThat(code(call(member, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true))))
                .isEqualTo("FORBIDDEN");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        assertThat(permissions(owner, org)).contains("business.view", "business.manage");
        assertThat(permissions(member, org)).contains("business.view").doesNotContain("business.manage");
        JsonNode settings = ok(call(member, HttpMethod.GET, biz + "/settings", null)).path("data");
        assertThat(settings.path("paymentTermsDays").asInt()).isEqualTo(30);
        assertThat(settings.path("vatDefault").asBoolean()).isTrue();
        // The default expense categories appear on first use.
        assertThat(ok(call(member, HttpMethod.GET, biz + "/expense-categories", null)).path("data").findValuesAsText("nameEn"))
                .contains("Office supplies", "Transport and fuel", "Other");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", false)));
        assertThat(code(call(member, HttpMethod.GET, biz + "/invoices", null))).isEqualTo("MODULE_DISABLED");
    }

    @Test
    void the_server_computes_totals_rounding_half_up_with_discounts_and_vat() throws Exception {
        Setup s = setup();
        String c = customer(s.member(), s, "Angkor Trading");
        Map<String, Object> body = new HashMap<>(Map.of("customerId", c, "lines", List.of(
                line("Paper", "1.5", "2.25", "15", true),        // 3.375 → 3.38, discount 0.507 → 0.51, net 2.87
                line("Printer", "3", "19.99", "0", false),       // 59.97, VAT-exempt
                line("Toner", "7", "1.11", "33.33", true))));    // 7.77, discount 2.589741 → 2.59, net 5.18
        body.put("total", "1.00"); // ignored: the client never sets totals
        body.put("subtotal", "1.00");
        JsonNode inv = ok(call(s.member(), HttpMethod.POST, s.biz() + "/invoices", body)).path("data");
        JsonNode lines = inv.path("lines");
        assertThat(dec(lines.get(0).path("discount"))).isEqualTo("0.51");
        assertThat(dec(lines.get(0).path("net"))).isEqualTo("2.87");
        assertThat(dec(lines.get(1).path("net"))).isEqualTo("59.97");
        assertThat(dec(lines.get(2).path("discount"))).isEqualTo("2.59");
        assertThat(dec(lines.get(2).path("net"))).isEqualTo("5.18");
        assertThat(dec(inv.path("subtotal"))).isEqualTo("68.02");
        assertThat(dec(inv.path("discountTotal"))).isEqualTo("3.10");
        // VAT 10% on the taxable 8.05 = 0.805 → 0.81 (half-up), once for the document.
        assertThat(dec(inv.path("vatTotal"))).isEqualTo("0.81");
        assertThat(dec(inv.path("total"))).isEqualTo("68.83");
        assertThat(inv.path("number").isNull()).isTrue();
        assertThat(inv.path("status").asText()).isEqualTo("DRAFT");

        // VAT switched off for the document: no VAT on any line.
        body.put("vatEnabled", false);
        JsonNode noVat = ok(call(s.member(), HttpMethod.PUT, s.biz() + "/invoices/" + inv.path("id").asText(), body)).path("data");
        assertThat(dec(noVat.path("vatTotal"))).isEqualTo("0.00");
        assertThat(dec(noVat.path("total"))).isEqualTo("68.02");

        // A 100% discount makes a free line; prices with more than two decimals and lines without text are refused.
        JsonNode free = ok(draft(s.member(), s, c, List.of(line("Gift", "2", "5.00", "100", true)))).path("data");
        assertThat(dec(free.path("total"))).isEqualTo("0.00");
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/invoices/" + free.path("id").asText() + "/issue", null)))
                .isEqualTo("INVALID_STATE");
        assertThat(code(draft(s.member(), s, c, List.of(line("Bad", "1", "0.125", "0", true))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(draft(s.member(), s, c, List.of(line(" ", "1", "1.00", "0", true))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(draft(s.member(), s, c, List.of(line("Neg", "-1", "1.00", "0", true))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(draft(s.member(), s, c, List.of(line("Over", "1", "1.00", "120", true))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(draft(s.member(), s, c, List.of()))).isEqualTo("VALIDATION_FAILED");

        // A product fills an empty description; its price is what the line says.
        String product = ok(call(s.owner(), HttpMethod.POST, s.biz() + "/products", Map.of("nameEn", "Bookkeeping",
                "nameKm", "ការកត់ត្រាបញ្ជី", "unit", "month", "unitPrice", "150.00", "vat", true))).path("data").path("id").asText();
        Map<String, Object> fromProduct = line("", "2", "150.00", "0", true);
        fromProduct.put("productId", product);
        JsonNode p = ok(draft(s.member(), s, c, List.of(fromProduct))).path("data");
        assertThat(p.path("lines").get(0).path("description").asText()).isEqualTo("Bookkeeping");
        assertThat(p.path("lines").get(0).path("unit").asText()).isEqualTo("month");
        assertThat(dec(p.path("total"))).isEqualTo("330.00");
    }

    @Test
    void invoice_numbers_never_repeat_even_when_issued_at_the_same_time() throws Exception {
        Setup s = setup();
        String c = customer(s.owner(), s, "Mekong Foods");
        List<String> drafts = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            drafts.add(simpleDraft(i % 2 == 0 ? s.owner() : s.admin(), s, c, "10.00"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<JsonNode>> issued = new ArrayList<>();
            for (int i = 0; i < drafts.size(); i++) {
                User who = i % 2 == 0 ? s.owner() : s.admin();
                String id = drafts.get(i);
                issued.add(pool.submit((Callable<JsonNode>) () -> call(who, HttpMethod.POST, s.biz() + "/invoices/" + id + "/issue", null)));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<JsonNode> f : issued) {
                numbers.add(ok(f.get()).path("data").path("number").asText());
            }
            assertThat(numbers).containsExactlyInAnyOrder("INV-0001", "INV-0002", "INV-0003", "INV-0004");
        } finally {
            pool.shutdown();
        }
        // Issuing twice is refused; the next one continues the sequence.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.biz() + "/invoices/" + drafts.get(0) + "/issue", null)))
                .isEqualTo("INVALID_STATE");
        String fifth = simpleDraft(s.owner(), s, c, "5.00");
        assertThat(ok(call(s.owner(), HttpMethod.POST, s.biz() + "/invoices/" + fifth + "/issue", null)).path("data")
                .path("number").asText()).isEqualTo("INV-0005");
        // Quotes have their own sequence, taken at creation; another organisation starts at 1.
        Map<String, Object> quote = Map.of("customerId", c, "issueDate", TODAY.toString(), "lines",
                List.of(line("Design", "1", "100.00", "0", true)));
        assertThat(ok(call(s.member(), HttpMethod.POST, s.biz() + "/quotes", quote)).path("data").path("number").asText())
                .isEqualTo("Q-0001");
        assertThat(ok(call(s.member(), HttpMethod.POST, s.biz() + "/quotes", quote)).path("data").path("number").asText())
                .isEqualTo("Q-0002");
        Setup t = setup();
        String tc = customer(t.owner(), t, "Other Co");
        String tid = simpleDraft(t.owner(), t, tc, "1.00");
        assertThat(ok(call(t.owner(), HttpMethod.POST, t.biz() + "/invoices/" + tid + "/issue", null)).path("data")
                .path("number").asText()).isEqualTo("INV-0001");
    }

    @Test
    void an_issued_invoice_takes_payments_until_paid_and_refuses_overpayment() throws Exception {
        Setup s = setup();
        String c = customer(s.member(), s, "Phnom Penh Bakery");
        String id = simpleDraft(s.member(), s, c, "100.00");
        // A draft takes no payment.
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/invoices/" + id + "/payments",
                Map.of("paidOn", TODAY.toString(), "amount", "10.00", "method", "CASH")))).isEqualTo("INVALID_STATE");

        JsonNode issued = ok(call(s.member(), HttpMethod.POST, s.biz() + "/invoices/" + id + "/issue", null)).path("data");
        assertThat(issued.path("status").asText()).isEqualTo("ISSUED");
        assertThat(issued.path("issueDate").asText()).isEqualTo(TODAY.toString());
        assertThat(issued.path("dueDate").asText()).isEqualTo(TODAY.plusDays(30).toString());
        assertThat(dec(issued.path("balance"))).isEqualTo("100.00");
        assertThat(dec(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers/" + c, null)).path("data").path("customer")
                .path("balance"))).isEqualTo("100.00");

        String pay = s.biz() + "/invoices/" + id + "/payments";
        JsonNode part = ok(call(s.admin(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "40.00",
                "method", "KHQR", "reference", "ABA 123"))).path("data");
        assertThat(part.path("status").asText()).isEqualTo("PARTIALLY_PAID");
        assertThat(dec(part.path("balance"))).isEqualTo("60.00");
        // More than the balance, a bad method or a date before the invoice are refused.
        assertThat(code(call(s.member(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "60.01",
                "method", "CASH")))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.member(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "1.00",
                "method", "CHEQUE")))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.member(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.minusDays(1).toString(), "amount", "1.00",
                "method", "CASH")))).isEqualTo("VALIDATION_FAILED");
        // Another member can't take payments on someone else's invoice.
        assertThat(code(call(s.other(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "1.00",
                "method", "CASH")))).isEqualTo("FORBIDDEN");

        JsonNode paid = ok(call(s.admin(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "60.00",
                "method", "BANK_TRANSFER"))).path("data");
        assertThat(paid.path("status").asText()).isEqualTo("PAID");
        assertThat(dec(paid.path("balance"))).isEqualTo("0.00");
        assertThat(paid.path("payments")).hasSize(2);
        assertThat(code(call(s.member(), HttpMethod.POST, pay, Map.of("paidOn", TODAY.toString(), "amount", "0.01",
                "method", "CASH")))).isEqualTo("INVALID_STATE");
        assertThat(dec(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers/" + c, null)).path("data").path("customer")
                .path("balance"))).isEqualTo("0.00");

        // The creator hears it was paid; the history tells the story.
        assertThat(ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data")
                .path("items").findValuesAsText("title")).contains("Invoice paid: INV-0001");
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/invoices/" + id + "/activity", null)).path("data")
                .findValuesAsText("verb")).contains("business.invoice.created", "business.invoice.issued",
                        "business.invoice.payment", "business.invoice.paid");
        // In the audit log with the invoice's number and link.
        JsonNode audit = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=business", null))
                .path("data").path("items");
        assertThat(audit.findValuesAsText("title")).contains("INV-0001 · Phnom Penh Bakery");

        // The overview counts the sale this month and nothing owed.
        JsonNode overview = ok(call(s.owner(), HttpMethod.GET, s.biz() + "/overview", null)).path("data");
        assertThat(dec(overview.path("salesThisMonth"))).isEqualTo("100.00");
        assertThat(dec(overview.path("receivable"))).isEqualTo("0.00");
        assertThat(overview.path("months")).hasSize(6);
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/overview", null)).path("data").path("expensesThisMonth")
                .isNull()).isTrue();
    }

    @Test
    void issued_invoices_are_fixed_and_only_admins_void_them() throws Exception {
        Setup s = setup();
        String c = customer(s.member(), s, "Kampot Pepper");
        String id = simpleDraft(s.member(), s, c, "50.00");
        String inv = s.biz() + "/invoices/" + id;
        // Drafts can't be voided (they are deleted instead).
        assertThat(code(call(s.owner(), HttpMethod.POST, inv + "/void", Map.of("reason", "Wrong")))).isEqualTo("INVALID_STATE");
        // Issue with dates in the past: overdue.
        ok(call(s.member(), HttpMethod.PUT, inv, Map.of("customerId", c, "issueDate", TODAY.minusDays(40).toString(),
                "dueDate", TODAY.minusDays(10).toString(), "lines", List.of(line("Pepper", "5", "10.00", "0", false)))));
        JsonNode issued = ok(call(s.member(), HttpMethod.POST, inv + "/issue", null)).path("data");
        assertThat(issued.path("overdue").asBoolean()).isTrue();
        JsonNode overdue = ok(call(s.member(), HttpMethod.GET, s.biz() + "/invoices?status=OVERDUE", null)).path("data").path("items");
        assertThat(overdue).hasSize(1);
        assertThat(overdue.get(0).path("id").asText()).isEqualTo(id);
        assertThat(dec(ok(call(s.owner(), HttpMethod.GET, s.biz() + "/overview", null)).path("data").path("overdue")))
                .isEqualTo("50.00");

        // Issued: no edits, no deleting.
        assertThat(code(call(s.member(), HttpMethod.PUT, inv, Map.of("customerId", c, "lines",
                List.of(line("Changed", "1", "1.00", "0", false)))))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.owner(), HttpMethod.DELETE, inv, null))).isEqualTo("INVALID_STATE");
        // Members can't void; with a payment it can't be voided until the payment is removed (admins only).
        ok(call(s.member(), HttpMethod.POST, inv + "/payments", Map.of("paidOn", TODAY.toString(), "amount", "5.00", "method", "CASH")));
        assertThat(code(call(s.member(), HttpMethod.POST, inv + "/void", Map.of("reason", "Wrong price")))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.owner(), HttpMethod.POST, inv + "/void", Map.of("reason", "Wrong price")))).isEqualTo("INVALID_STATE");
        String paymentId = ok(call(s.member(), HttpMethod.GET, inv, null)).path("data").path("payments").get(0).path("id").asText();
        assertThat(code(call(s.member(), HttpMethod.DELETE, inv + "/payments/" + paymentId, null))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.admin(), HttpMethod.DELETE, inv + "/payments/" + paymentId, null)).path("data").path("status")
                .asText()).isEqualTo("ISSUED");
        assertThat(code(call(s.owner(), HttpMethod.POST, inv + "/void", Map.of("reason", " ")))).isEqualTo("VALIDATION_FAILED");
        JsonNode voided = ok(call(s.owner(), HttpMethod.POST, inv + "/void", Map.of("reason", "Wrong price"))).path("data");
        assertThat(voided.path("status").asText()).isEqualTo("VOID");
        assertThat(voided.path("voidReason").asText()).isEqualTo("Wrong price");
        assertThat(voided.path("overdue").asBoolean()).isFalse();
        assertThat(code(call(s.owner(), HttpMethod.POST, inv + "/void", Map.of("reason", "Again")))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.member(), HttpMethod.POST, inv + "/payments", Map.of("paidOn", TODAY.toString(), "amount", "5.00",
                "method", "CASH")))).isEqualTo("INVALID_STATE");
        // A void invoice owes nothing; its number stays used.
        assertThat(dec(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers/" + c, null)).path("data").path("customer")
                .path("balance"))).isEqualTo("0.00");
        String next = simpleDraft(s.member(), s, c, "50.00");
        assertThat(ok(call(s.member(), HttpMethod.POST, s.biz() + "/invoices/" + next + "/issue", null)).path("data")
                .path("number").asText()).isEqualTo("INV-0002");
        // A draft is deleted by its creator.
        String gone = simpleDraft(s.member(), s, c, "1.00");
        ok(call(s.member(), HttpMethod.DELETE, s.biz() + "/invoices/" + gone, null));
        assertThat(code(call(s.member(), HttpMethod.GET, s.biz() + "/invoices/" + gone, null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void a_quote_is_sent_accepted_and_converted_into_a_draft_invoice_once() throws Exception {
        Setup s = setup();
        String c = customer(s.member(), s, "Siem Reap Tours");
        Map<String, Object> body = Map.of("customerId", c, "issueDate", TODAY.toString(),
                "validUntil", TODAY.plusDays(30).toString(), "notes", "Valid for 30 days",
                "lines", List.of(line("Tour package", "4", "35.50", "10", true), line("Guide", "1", "20.00", "0", false)));
        JsonNode q = ok(call(s.member(), HttpMethod.POST, s.biz() + "/quotes", body)).path("data");
        String quote = s.biz() + "/quotes/" + q.path("id").asText();
        // 142.00 − 14.20 = 127.80; + 20.00 = 147.80; VAT 12.78 → 160.58.
        assertThat(dec(q.path("total"))).isEqualTo("160.58");
        assertThat(q.path("status").asText()).isEqualTo("DRAFT");
        assertThat(code(call(s.member(), HttpMethod.POST, quote + "/accept", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.other(), HttpMethod.POST, quote + "/send", null))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.member(), HttpMethod.POST, quote + "/send", null)).path("data").path("status").asText()).isEqualTo("SENT");
        assertThat(ok(call(s.member(), HttpMethod.POST, quote + "/accept", null)).path("data").path("status").asText())
                .isEqualTo("ACCEPTED");

        JsonNode inv = ok(call(s.member(), HttpMethod.POST, quote + "/convert", null)).path("data");
        assertThat(inv.path("status").asText()).isEqualTo("DRAFT");
        assertThat(dec(inv.path("total"))).isEqualTo("160.58");
        assertThat(inv.path("lines")).hasSize(2);
        assertThat(inv.path("quoteNumber").asText()).isEqualTo(q.path("number").asText());
        assertThat(inv.path("notes").asText()).isEqualTo("Valid for 30 days");
        assertThat(code(call(s.member(), HttpMethod.POST, quote + "/convert", null))).isEqualTo("INVALID_STATE");
        JsonNode after = ok(call(s.member(), HttpMethod.GET, quote, null)).path("data");
        assertThat(after.path("invoiceId").asText()).isEqualTo(inv.path("id").asText());
        assertThat(after.path("canEdit").asBoolean()).isFalse();
        assertThat(code(call(s.member(), HttpMethod.PUT, quote, body))).isEqualTo("INVALID_STATE");

        // An expired quote can't be accepted; it can be declined.
        Map<String, Object> old = Map.of("customerId", c, "issueDate", TODAY.minusDays(20).toString(),
                "validUntil", TODAY.minusDays(5).toString(), "lines", List.of(line("Old offer", "1", "10.00", "0", true)));
        String expired = s.biz() + "/quotes/" + ok(call(s.member(), HttpMethod.POST, s.biz() + "/quotes", old)).path("data").path("id").asText();
        ok(call(s.member(), HttpMethod.POST, expired + "/send", null));
        assertThat(ok(call(s.member(), HttpMethod.GET, expired, null)).path("data").path("expired").asBoolean()).isTrue();
        assertThat(code(call(s.member(), HttpMethod.POST, expired + "/accept", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.member(), HttpMethod.POST, expired + "/convert", null))).isEqualTo("INVALID_STATE");
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/quotes?status=EXPIRED", null)).path("data").path("items"))
                .hasSize(1);
        assertThat(ok(call(s.member(), HttpMethod.POST, expired + "/decline", null)).path("data").path("status").asText())
                .isEqualTo("DECLINED");
        // A valid-until date before the quote date is refused.
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/quotes", Map.of("customerId", c,
                "issueDate", TODAY.toString(), "validUntil", TODAY.minusDays(1).toString(),
                "lines", List.of(line("x", "1", "1.00", "0", true)))))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void expenses_go_through_the_approval_engine_and_only_approved_ones_are_paid() throws Exception {
        Setup s = setup();
        String fuel = null;
        for (JsonNode cat : ok(call(s.member(), HttpMethod.GET, s.biz() + "/expense-categories", null)).path("data")) {
            if (cat.path("nameEn").asText().equals("Transport and fuel")) {
                fuel = cat.path("id").asText();
            }
        }
        Map<String, Object> body = new HashMap<>(Map.of("spentOn", TODAY.toString(), "categoryId", fuel, "supplier", "Tela",
                "amount", "22.00", "vatAmount", "2.00", "notes", "Delivery trip", "submit", false));
        JsonNode draft = ok(call(s.member(), HttpMethod.POST, s.biz() + "/expenses", body)).path("data");
        String exp = s.biz() + "/expenses/" + draft.path("id").asText();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        // A receipt (a photo or PDF) goes on it; other people can't see it.
        mvc.perform(multipart(exp + "/receipt").file(new MockMultipartFile("file", "receipt.pdf", "application/pdf",
                "%PDF-1.4 receipt".getBytes())).header("Authorization", "Bearer " + s.member().token())).andReturn();
        assertThat(ok(call(s.member(), HttpMethod.GET, exp, null)).path("data").path("receipt").path("name").asText())
                .isEqualTo("receipt.pdf");
        assertThat(code(call(s.other(), HttpMethod.GET, exp, null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(s.other(), HttpMethod.GET, s.biz() + "/expenses", null)).path("data").path("items")).isEmpty();
        // VAT above the amount is refused; an admin can't submit someone else's expense.
        Map<String, Object> bad = new HashMap<>(body);
        bad.put("vatAmount", "30.00");
        assertThat(code(call(s.member(), HttpMethod.PUT, exp, bad))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.owner(), HttpMethod.POST, exp + "/submit", null))).isEqualTo("FORBIDDEN");

        JsonNode pending = ok(call(s.member(), HttpMethod.POST, exp + "/submit", null)).path("data");
        assertThat(pending.path("status").asText()).isEqualTo("PENDING");
        assertThat(pending.path("approvalStatus").asText()).isEqualTo("PENDING");
        assertThat(code(call(s.member(), HttpMethod.PUT, exp, body))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.owner(), HttpMethod.POST, exp + "/paid", null))).isEqualTo("INVALID_STATE");
        // The default workflow "Expense approval" was created; the admins find it in their inbox.
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/workflows", null)).path("data")
                .findValuesAsText("name")).contains("Expense approval");
        JsonNode inbox = ok(call(s.admin(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data");
        assertThat(inbox.findValuesAsText("title")).contains("Expense · Transport and fuel · $22.00 · Sok Seller");
        String approval = pending.path("approvalId").asText();
        assertThat(code(call(s.other(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve",
                Map.of()))).isIn("FORBIDDEN", "NOT_FOUND");
        ok(call(s.admin(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve", Map.of()));
        JsonNode approved = ok(call(s.member(), HttpMethod.GET, exp, null)).path("data");
        assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        assertThat(ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data")
                .path("items").findValuesAsText("type")).contains("approval.approved");
        // Only admins mark it paid; the person hears about it; the expense shows in this month's totals.
        assertThat(code(call(s.member(), HttpMethod.POST, exp + "/paid", null))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.owner(), HttpMethod.POST, exp + "/paid", null)).path("data").path("status").asText()).isEqualTo("PAID");
        assertThat(ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data")
                .path("items").findValuesAsText("type")).contains("business.expense.paid");
        assertThat(dec(ok(call(s.owner(), HttpMethod.GET, s.biz() + "/overview", null)).path("data").path("expensesThisMonth")))
                .isEqualTo("22.00");

        // Rejected: never paid.
        body.put("submit", true);
        JsonNode second = ok(call(s.member(), HttpMethod.POST, s.biz() + "/expenses", body)).path("data");
        assertThat(second.path("status").asText()).isEqualTo("PENDING");
        assertThat(code(call(s.owner(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + second.path("approvalId").asText()
                + "/reject", Map.of()))).isEqualTo("VALIDATION_FAILED");
        ok(call(s.owner(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + second.path("approvalId").asText() + "/reject",
                Map.of("comment", "Personal trip")));
        String secondPath = s.biz() + "/expenses/" + second.path("id").asText();
        assertThat(ok(call(s.member(), HttpMethod.GET, secondPath, null)).path("data").path("status").asText()).isEqualTo("REJECTED");
        assertThat(code(call(s.owner(), HttpMethod.POST, secondPath + "/paid", null))).isEqualTo("INVALID_STATE");

        // Withdrawn: back to a draft that can be changed and sent again.
        JsonNode third = ok(call(s.member(), HttpMethod.POST, s.biz() + "/expenses", body)).path("data");
        ok(call(s.member(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + third.path("approvalId").asText() + "/withdraw", null));
        String thirdPath = s.biz() + "/expenses/" + third.path("id").asText();
        assertThat(ok(call(s.member(), HttpMethod.GET, thirdPath, null)).path("data").path("status").asText()).isEqualTo("DRAFT");
        body.put("submit", false);
        body.put("amount", "18.00");
        assertThat(dec(ok(call(s.member(), HttpMethod.PUT, thirdPath, body)).path("data").path("amount"))).isEqualTo("18.00");
        assertThat(ok(call(s.member(), HttpMethod.POST, thirdPath + "/submit", null)).path("data").path("status").asText())
                .isEqualTo("PENDING");

        // Admins see everyone's expenses; members only their own.
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.biz() + "/expenses", null)).path("data").path("total").asInt()).isEqualTo(3);
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.biz() + "/expenses?mine=true", null)).path("data").path("total").asInt()).isZero();
    }

    @Test
    void an_organisation_whose_only_admin_submits_gets_the_expense_approved_at_once() throws Exception {
        User solo = signUp("Solo Owner");
        String org = newOrg(solo);
        ok(call(solo, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        String biz = "/api/orgs/" + org + "/business";
        String cat = ok(call(solo, HttpMethod.GET, biz + "/expense-categories", null)).path("data").get(0).path("id").asText();
        JsonNode e = ok(call(solo, HttpMethod.POST, biz + "/expenses", Map.of("spentOn", TODAY.toString(), "categoryId", cat,
                "amount", "9.50", "submit", true))).path("data");
        assertThat(e.path("status").asText()).isEqualTo("APPROVED");
        assertThat(ok(call(solo, HttpMethod.GET, "/api/orgs/" + org + "/activity?module=business", null)).path("data")
                .path("items").findValuesAsText("verb")).contains("business.expense.self_approved");
    }

    @Test
    void members_work_on_their_own_documents_admins_on_everything_and_guests_on_nothing() throws Exception {
        Setup s = setup();
        // Guests get nothing.
        for (String path : List.of("/overview", "/customers", "/products", "/invoices", "/expenses")) {
            assertThat(code(call(s.guest(), HttpMethod.GET, s.biz() + path, null))).as(path).isEqualTo("FORBIDDEN");
        }
        // Products, categories, settings and archiving are for admins.
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/products", Map.of("nameEn", "X", "unit", "pcs",
                "unitPrice", "1.00", "vat", true)))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/expense-categories", Map.of("nameEn", "X",
                "nameKm", "X")))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.member(), HttpMethod.PUT, s.biz() + "/settings", Map.of("paymentTermsDays", 14,
                "vatDefault", true)))).isEqualTo("FORBIDDEN");
        String c = customer(s.member(), s, "Battambang Rice");
        assertThat(code(call(s.member(), HttpMethod.POST, s.biz() + "/customers/" + c + "/archive", null))).isEqualTo("FORBIDDEN");
        // Members edit customers; settings change the defaults of new invoices.
        ok(call(s.other(), HttpMethod.PUT, s.biz() + "/customers/" + c, Map.of("kind", "COMPANY", "name", "Battambang Rice Co",
                "phone", "+855 12 345 678")));
        ok(call(s.owner(), HttpMethod.PUT, s.biz() + "/settings", Map.of("legalName", "Biz Co., Ltd.", "taxId", "L001-100",
                "invoiceFooter", "ABA 000 111 222", "paymentTermsDays", 14, "vatDefault", false)));
        String mine = simpleDraft(s.member(), s, c, "20.00");
        JsonNode d = ok(call(s.member(), HttpMethod.GET, s.biz() + "/invoices/" + mine, null)).path("data");
        assertThat(d.path("vatEnabled").asBoolean()).isFalse();
        assertThat(d.path("canEdit").asBoolean()).isTrue();
        assertThat(d.path("canVoid").asBoolean()).isFalse();
        // Another member sees it but can't change, issue or delete it; an admin can.
        JsonNode seen = ok(call(s.other(), HttpMethod.GET, s.biz() + "/invoices/" + mine, null)).path("data");
        assertThat(seen.path("canEdit").asBoolean()).isFalse();
        Map<String, Object> change = Map.of("customerId", c, "lines", List.of(line("Rice", "10", "2.00", "0", false)));
        assertThat(code(call(s.other(), HttpMethod.PUT, s.biz() + "/invoices/" + mine, change))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.other(), HttpMethod.POST, s.biz() + "/invoices/" + mine + "/issue", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.other(), HttpMethod.DELETE, s.biz() + "/invoices/" + mine, null))).isEqualTo("FORBIDDEN");
        ok(call(s.admin(), HttpMethod.PUT, s.biz() + "/invoices/" + mine, change));
        JsonNode issued = ok(call(s.admin(), HttpMethod.POST, s.biz() + "/invoices/" + mine + "/issue", null)).path("data");
        assertThat(issued.path("dueDate").asText()).isEqualTo(TODAY.plusDays(14).toString());

        // Archived customers stay on their invoices but can't go on new ones.
        ok(call(s.owner(), HttpMethod.POST, s.biz() + "/customers/" + c + "/archive", null));
        assertThat(code(draft(s.member(), s, c, List.of(line("x", "1", "1.00", "0", false))))).isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers", null)).path("data").path("items")).isEmpty();
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers?archived=true", null)).path("data").path("items"))
                .hasSize(1);
        ok(call(s.owner(), HttpMethod.POST, s.biz() + "/customers/" + c + "/restore", null));

        // Invoice due dates on the calendar for those who run the business, not for members.
        String cal = "/api/orgs/" + s.org() + "/calendar?from=" + TODAY + "&to=" + TODAY.plusDays(20);
        assertThat(ok(call(s.owner(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("key"))
                .contains("INV-0001");
        assertThat(ok(call(s.member(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("key"))
                .doesNotContain("INV-0001");
        // Search by number or customer name.
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/invoices?q=battambang", null)).path("data").path("total")
                .asInt()).isEqualTo(1);
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/invoices?q=INV-0001", null)).path("data").path("total")
                .asInt()).isEqualTo(1);
        assertThat(ok(call(s.member(), HttpMethod.GET, s.biz() + "/customers?q=12 345", null)).path("data").path("total")
                .asInt()).isEqualTo(1);
    }

    @Test
    void other_organisations_see_and_use_nothing() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String c = customer(a.owner(), a, "Private Customer");
        String inv = simpleDraft(a.owner(), a, c, "10.00");
        // Not a member: the organisation doesn't exist for them.
        assertThat(code(call(b.owner(), HttpMethod.GET, a.biz() + "/invoices", null))).isEqualTo("NOT_FOUND");
        // Through their own organisation, another's records are not found and can't be used.
        assertThat(code(call(b.owner(), HttpMethod.GET, b.biz() + "/invoices/" + inv, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.GET, b.biz() + "/customers/" + c, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.POST, b.biz() + "/invoices/" + inv + "/issue", null))).isEqualTo("NOT_FOUND");
        assertThat(code(draft(b.owner(), b, c, List.of(line("x", "1", "1.00", "0", false))))).isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(b.owner(), HttpMethod.GET, b.biz() + "/customers", null)).path("data").path("items")).isEmpty();
        assertThat(ok(call(b.owner(), HttpMethod.GET, b.biz() + "/invoices", null)).path("data").path("items")).isEmpty();
    }
}
