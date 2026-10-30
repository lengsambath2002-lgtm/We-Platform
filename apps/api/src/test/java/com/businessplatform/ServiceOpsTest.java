package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.businessplatform.ops.service.MaintenanceJob;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Service &amp; Operations: module switch and roles (admins, dispatchers, technicians, members without a role,
 * guests), ticket numbers, status flow and derived SLA, the work order journey to a technician with server-side
 * totals, approval above the threshold, idempotent preventive maintenance, the calendar and isolation between
 * organisations.
 */
class ServiceOpsTest extends IntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final LocalDate TODAY = LocalDate.now(ZONE);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MaintenanceJob job;

    /** Owner and Admin; a dispatcher; two technicians; a member without a service role; a guest. Module on. */
    private record Setup(User owner, User admin, User dispatcher, User tech, User tech2, User member, User guest, String org) {

        String svc() {
            return "/api/orgs/" + org + "/service";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Svc Owner");
        User admin = signUp("Svc Admin");
        User dispatcher = signUp("Dara Dispatch");
        User tech = signUp("Sok Technician");
        User tech2 = signUp("Vibol Technician");
        User member = signUp("Plain Member");
        User guest = signUp("Svc Guest");
        String org = newOrg(owner);
        join(owner, org, admin, "ADMIN");
        for (User u : List.of(dispatcher, tech, tech2, member)) {
            join(owner, org, u, "MEMBER");
        }
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/service", Map.of("enabled", true)));
        Setup s = new Setup(owner, admin, dispatcher, tech, tech2, member, guest, org);
        staff(s, dispatcher, "DISPATCHER");
        staff(s, tech, "TECHNICIAN");
        staff(s, tech2, "TECHNICIAN");
        return s;
    }

    private String staff(Setup s, User who, String role) throws Exception {
        return ok(call(s.owner(), HttpMethod.POST, s.svc() + "/staff", Map.of("userId", who.id(), "role", role,
                "skills", List.of("Air conditioning", "Electrical")))).path("data").path("id").asText();
    }

    private String customer(Setup s, String name) throws Exception {
        return ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/customers", Map.of("name", name, "phone", "+855 12 345 678")))
                .path("data").path("id").asText();
    }

    private String asset(Setup s, String customerId, String serial) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("customerId", customerId, "name", "Split AC 2HP", "serial", serial,
                "model", "Daikin FTKC50", "warrantyEnd", TODAY.plusYears(1).toString()));
        return ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/assets", body)).path("data").path("id").asText();
    }

    private JsonNode ticket(Setup s, User by, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("title", "AC not cooling", "category", "REPAIR", "priority", "HIGH"));
        body.putAll(extra);
        return call(by, HttpMethod.POST, s.svc() + "/tickets", body);
    }

    private static Map<String, Object> line(String kind, String description, String qty, String price) {
        return Map.of("kind", kind, "description", description, "quantity", qty, "unitPrice", price);
    }

    private static Map<String, Object> workOrder(Map<String, Object> extra) {
        Instant start = TODAY.plusDays(1).atTime(9, 0).atZone(ZONE).toInstant();
        Map<String, Object> body = new HashMap<>(Map.of("title", "Repair split AC", "priority", "HIGH",
                "scheduledStart", start.toString(), "scheduledEnd", start.plus(2, ChronoUnit.HOURS).toString()));
        body.putAll(extra);
        return body;
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

    private List<String> notificationTitles(User u, String org) throws Exception {
        return ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/notifications", null)).path("data").findValuesAsText("title");
    }

    @Test
    void the_module_is_off_until_switched_on_and_only_service_roles_get_in() throws Exception {
        User owner = signUp("Off Owner");
        String org = newOrg(owner);
        String svc = "/api/orgs/" + org + "/service";
        for (String path : List.of("/overview", "/tickets", "/work-orders", "/assets", "/staff", "/reports")) {
            assertThat(code(call(owner, HttpMethod.GET, svc + path, null))).as(path).isEqualTo("MODULE_DISABLED");
        }
        assertThat(permissions(owner, org)).noneMatch(p -> p.startsWith("service."));

        Setup s = setup();
        assertThat(permissions(s.admin(), s.org())).contains("service.view", "service.manage", "service.admin")
                .doesNotContain("service.technician");
        assertThat(permissions(s.dispatcher(), s.org())).contains("service.view", "service.manage")
                .doesNotContain("service.admin", "service.technician");
        assertThat(permissions(s.tech(), s.org())).contains("service.view", "service.technician")
                .doesNotContain("service.manage");
        assertThat(permissions(s.member(), s.org())).noneMatch(p -> p.startsWith("service."));
        assertThat(permissions(s.guest(), s.org())).noneMatch(p -> p.startsWith("service."));
        // Members without a service role, and guests, get nothing.
        for (User u : List.of(s.member(), s.guest())) {
            for (String path : List.of("/overview", "/tickets", "/work-orders", "/me")) {
                assertThat(code(call(u, HttpMethod.GET, s.svc() + path, null))).as(path).isEqualTo("FORBIDDEN");
            }
        }
        // Technicians: no customers, assets, staff, reports or new tickets.
        for (String path : List.of("/customers", "/assets", "/staff", "/reports", "/maintenance")) {
            assertThat(code(call(s.tech(), HttpMethod.GET, s.svc() + path, null))).as(path).isEqualTo("FORBIDDEN");
        }
        assertThat(code(ticket(s, s.tech(), Map.of()))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.tech(), HttpMethod.POST, s.svc() + "/work-orders", workOrder(Map.of())))).isEqualTo("FORBIDDEN");
        // Dispatchers run the work but not the staff, SLA or settings.
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/staff", Map.of("userId", s.member().id(),
                "role", "TECHNICIAN")))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.dispatcher(), HttpMethod.PUT, s.svc() + "/settings", Map.of("labourRate", "20.00"))))
                .isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.tech(), HttpMethod.GET, s.svc() + "/me", null)).path("data").path("role").asText()).isEqualTo("TECHNICIAN");
        // A staff row switched off takes the access away.
        String row = ok(call(s.admin(), HttpMethod.GET, s.svc() + "/staff", null)).path("data").findParents("role").stream()
                .filter(n -> n.path("person").path("id").asText().equals(s.tech2().id().toString())).findFirst().orElseThrow()
                .path("id").asText();
        ok(call(s.admin(), HttpMethod.PUT, s.svc() + "/staff/" + row, Map.of("role", "TECHNICIAN", "active", false)));
        assertThat(code(call(s.tech2(), HttpMethod.GET, s.svc() + "/overview", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.admin(), HttpMethod.POST, s.svc() + "/staff", Map.of("userId", s.tech().id(), "role", "TECHNICIAN"))))
                .isEqualTo("CONFLICT");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/service", Map.of("enabled", true)));
        ok(call(s.owner(), HttpMethod.PUT, "/api/orgs/" + s.org() + "/modules/service", Map.of("enabled", false)));
        assertThat(code(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/tickets", null))).isEqualTo("MODULE_DISABLED");
    }

    @Test
    void tickets_are_numbered_move_through_their_flow_and_derive_their_sla() throws Exception {
        Setup s = setup();
        String c = customer(s, "Angkor Hotel");
        JsonNode first = ok(ticket(s, s.dispatcher(), Map.of("customerId", c, "priority", "URGENT"))).path("data");
        assertThat(first.path("summary").path("number").asText()).isEqualTo("TCK-0001");
        assertThat(first.path("summary").path("sla").asText()).isEqualTo("ON_TRACK");
        Instant created = Instant.parse(first.path("summary").path("createdAt").asText());
        // Urgent default: respond within 1 hour, resolve within 4.
        assertThat(Instant.parse(first.path("summary").path("responseDueAt").asText())).isEqualTo(created.plus(60, ChronoUnit.MINUTES));
        assertThat(Instant.parse(first.path("summary").path("resolutionDueAt").asText())).isEqualTo(created.plus(240, ChronoUnit.MINUTES));
        String id = first.path("summary").path("id").asText();
        assertThat(ok(ticket(s, s.admin(), Map.of())).path("data").path("summary").path("number").asText()).isEqualTo("TCK-0002");

        // Moving the clock: the response target passed without a response → breached; another at 80% → at risk.
        jdbc.update("UPDATE svc_ticket SET created_at = now() - interval '2 hours', response_warn_at = now() - interval '75 minutes',"
                + " response_due_at = now() - interval '1 hour', resolution_warn_at = now() + interval '1 hour',"
                + " resolution_due_at = now() + interval '2 hours' WHERE id = ?", UUID.fromString(id));
        String risky = ok(ticket(s, s.dispatcher(), Map.of("priority", "LOW"))).path("data").path("summary").path("id").asText();
        jdbc.update("UPDATE svc_ticket SET response_warn_at = now() - interval '1 minute' WHERE id = ?", UUID.fromString(risky));
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/tickets/" + id, null)).path("data").path("summary")
                .path("sla").asText()).isEqualTo("BREACHED");
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/tickets?sla=BREACHED", null)).path("data")
                .path("items").findValuesAsText("number")).containsExactly("TCK-0001");
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/tickets?sla=AT_RISK", null)).path("data")
                .path("items").findValuesAsText("id")).containsExactly(risky);
        JsonNode overview = ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/overview", null)).path("data");
        assertThat(overview.path("breached").asInt()).isEqualTo(1);
        assertThat(overview.path("atRisk").asInt()).isEqualTo(1);
        assertThat(overview.path("ticketsByStatus").path("NEW").asInt()).isEqualTo(3);
        assertThat(overview.path("weeks")).hasSize(8);

        // Status flow: NEW → TRIAGED → IN_PROGRESS → WAITING → RESOLVED → CLOSED → reopened; no jumping back to NEW.
        String t = s.svc() + "/tickets/" + id;
        ok(call(s.dispatcher(), HttpMethod.POST, t + "/status", Map.of("status", "TRIAGED")));
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, t + "/status", Map.of("status", "NEW")))).isEqualTo("INVALID_STATE");
        // The response came late: it stays breached even after responding.
        JsonNode triaged = ok(call(s.dispatcher(), HttpMethod.GET, t, null)).path("data");
        assertThat(triaged.path("responseSla").asText()).isEqualTo("BREACHED");
        ok(call(s.dispatcher(), HttpMethod.POST, t + "/assign", Map.of("assigneeId", s.tech().id())));
        assertThat(notificationTitles(s.tech(), s.org())).contains("Ticket assigned to you: TCK-0001");
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, t + "/assign", Map.of("assigneeId", s.member().id()))))
                .isEqualTo("VALIDATION_FAILED");

        // The technician sees only their tickets, works them, but doesn't close or reassign.
        assertThat(ok(call(s.tech(), HttpMethod.GET, s.svc() + "/tickets", null)).path("data").path("items")
                .findValuesAsText("number")).containsExactly("TCK-0001");
        assertThat(code(call(s.tech(), HttpMethod.GET, s.svc() + "/tickets/" + risky, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.tech2(), HttpMethod.GET, t, null))).isEqualTo("NOT_FOUND");
        ok(call(s.tech(), HttpMethod.POST, t + "/status", Map.of("status", "IN_PROGRESS")));
        ok(call(s.tech(), HttpMethod.POST, t + "/status", Map.of("status", "WAITING")));
        ok(call(s.tech(), HttpMethod.POST, t + "/comments", Map.of("body", "Waiting for the compressor part")));
        assertThat(notificationTitles(s.dispatcher(), s.org())).contains("New comment on TCK-0001");
        mvc.perform(multipart(t + "/attachments").file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] { 1, 2, 3 }))
                .header("Authorization", "Bearer " + s.tech().token())).andReturn();
        JsonNode withFile = ok(call(s.tech(), HttpMethod.GET, t, null)).path("data");
        assertThat(withFile.path("attachments").get(0).path("name").asText()).isEqualTo("photo.jpg");
        String fileId = withFile.path("attachments").get(0).path("id").asText();
        var download = mvc.perform(get(t + "/attachments/" + fileId).header("Authorization", "Bearer " + s.dispatcher().token()))
                .andReturn().getResponse();
        assertThat(download.getStatus()).isEqualTo(200);
        assertThat(download.getContentAsByteArray()).containsExactly(1, 2, 3);
        assertThat(code(call(s.tech(), HttpMethod.POST, t + "/assign", Map.of("assigneeId", s.tech2().id())))).isEqualTo("FORBIDDEN");
        ok(call(s.tech(), HttpMethod.POST, t + "/status", Map.of("status", "RESOLVED")));
        assertThat(code(call(s.tech(), HttpMethod.POST, t + "/status", Map.of("status", "CLOSED")))).isEqualTo("FORBIDDEN");
        ok(call(s.dispatcher(), HttpMethod.POST, t + "/status", Map.of("status", "CLOSED")));
        JsonNode reopened = ok(call(s.dispatcher(), HttpMethod.POST, t + "/status", Map.of("status", "IN_PROGRESS"))).path("data");
        assertThat(reopened.path("closedAt").isNull()).isTrue();
        assertThat(reopened.path("resolvedAt").isNull()).isTrue();
        List<String> verbs = ok(call(s.dispatcher(), HttpMethod.GET, t + "/activity", null)).path("data").findValuesAsText("verb");
        assertThat(verbs).contains("service.ticket.created", "service.ticket.assigned", "service.ticket.commented",
                "service.ticket.attached", "service.ticket.reopened");
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, t + "/comments", null)).path("data").findValuesAsText("body"))
                .containsExactly("Waiting for the compressor part");

        // A priority change takes the new priority's targets (counted from when it came in).
        Map<String, Object> change = new HashMap<>(Map.of("title", "AC not cooling", "category", "REPAIR", "priority", "LOW",
                "customerId", c));
        JsonNode low = ok(call(s.dispatcher(), HttpMethod.PUT, t, change)).path("data").path("summary");
        Instant came = Instant.parse(low.path("createdAt").asText());
        assertThat(Instant.parse(low.path("resolutionDueAt").asText())).isEqualTo(came.plus(7200, ChronoUnit.MINUTES));
    }

    @Test
    void a_work_order_goes_from_a_ticket_to_its_technician_with_totals_computed_by_the_server() throws Exception {
        Setup s = setup();
        String c = customer(s, "Phnom Penh Clinic");
        String a = asset(s, c, "SN-001");
        String tk = ok(ticket(s, s.dispatcher(), Map.of("customerId", c, "assetId", a))).path("data").path("summary").path("id").asText();
        Map<String, Object> body = workOrder(Map.of("ticketId", tk, "technicianIds", List.of(s.tech().id()),
                "checklist", List.of("Check refrigerant pressure", "Clean filters"),
                "lines", List.of(line("LABOUR", "Diagnosis and repair", "1.333", "15.00"),  // 19.995 → 20.00
                        line("PART", "Refrigerant R32 (kg)", "0.75", "18.50"))));            // 13.875 → 13.88
        body.put("total", "1.00"); // ignored
        JsonNode wo = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders", body)).path("data");
        String id = wo.path("summary").path("id").asText();
        assertThat(wo.path("summary").path("number").asText()).isEqualTo("WO-0001");
        assertThat(wo.path("summary").path("status").asText()).isEqualTo("DRAFT");
        // From the ticket: its customer and asset.
        assertThat(wo.path("summary").path("customer").path("id").asText()).isEqualTo(c);
        assertThat(wo.path("summary").path("asset").path("id").asText()).isEqualTo(a);
        assertThat(dec(wo.path("labourTotal"))).isEqualTo("20.00");
        assertThat(dec(wo.path("partsTotal"))).isEqualTo("13.88");
        assertThat(dec(wo.path("summary").path("total"))).isEqualTo("33.88");
        assertThat(dec(wo.path("lines").get(0).path("amount"))).isEqualTo("20.00");
        // Prices have at most 2 decimals.
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders",
                workOrder(Map.of("lines", List.of(line("PART", "x", "1", "2.675"))))))).isEqualTo("VALIDATION_FAILED");
        // The ticket is being worked on.
        JsonNode t = ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/tickets/" + tk, null)).path("data");
        assertThat(t.path("summary").path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(t.path("workOrders").findValuesAsText("number")).containsExactly("WO-0001");

        // Only technicians can be assigned; another technician doesn't see it.
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders",
                workOrder(Map.of("technicianIds", List.of(s.member().id())))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.tech2(), HttpMethod.GET, s.svc() + "/work-orders/" + id, null))).isEqualTo("NOT_FOUND");

        // Schedule (under the $500 default threshold): scheduled at once; the technician hears about it.
        JsonNode scheduled = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders/" + id + "/schedule", null)).path("data");
        assertThat(scheduled.path("summary").path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(notificationTitles(s.tech(), s.org())).anyMatch(x -> x.startsWith("Work order for you: WO-0001"));
        // On the technician's calendar, not on the other technician's.
        String cal = "/api/orgs/" + s.org() + "/calendar?from=" + TODAY + "&to=" + TODAY.plusDays(3);
        assertThat(ok(call(s.tech(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("key")).contains("WO-0001");
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("key")).contains("WO-0001");

        // The technician's "My work orders" and the work itself.
        assertThat(ok(call(s.tech(), HttpMethod.GET, s.svc() + "/work-orders", null)).path("data").path("items")
                .findValuesAsText("number")).containsExactly("WO-0001");
        assertThat(ok(call(s.tech2(), HttpMethod.GET, s.svc() + "/work-orders", null)).path("data").path("items")).isEmpty();
        String w = s.svc() + "/work-orders/" + id;
        assertThat(code(call(s.tech(), HttpMethod.PUT, w + "/lines", Map.of("lines", List.of()))))
                .isEqualTo("INVALID_STATE"); // not started yet
        assertThat(code(call(s.tech2(), HttpMethod.POST, w + "/start", null))).isEqualTo("NOT_FOUND");
        ok(call(s.tech(), HttpMethod.POST, w + "/start", null));
        ok(call(s.tech(), HttpMethod.POST, w + "/hold", Map.of("reason", "Waiting for a part")));
        ok(call(s.tech(), HttpMethod.POST, w + "/start", null));
        JsonNode more = ok(call(s.tech(), HttpMethod.PUT, w + "/lines", Map.of("lines", List.of(
                line("LABOUR", "Diagnosis and repair", "2", "15.00"), line("PART", "Capacitor 35µF", "1", "2.68"),
                line("PART", "Refrigerant R32 (kg)", "0.75", "18.50"))))).path("data"); // 13.875 → 13.88
        assertThat(dec(more.path("summary").path("total"))).isEqualTo("46.56");
        assertThat(code(call(s.tech(), HttpMethod.POST, w + "/complete", Map.of("signedBy", "Dr. Chan"))))
                .isEqualTo("VALIDATION_FAILED"); // checklist not ticked
        for (JsonNode item : more.path("checklist")) {
            ok(call(s.tech(), HttpMethod.POST, w + "/checklist/" + item.path("id").asText(), Map.of("done", true)));
        }
        assertThat(code(call(s.tech(), HttpMethod.POST, w + "/complete", Map.of("notes", "Replaced capacitor")))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.tech(), HttpMethod.POST, w + "/cancel", Map.of("reason", "no")))).isEqualTo("FORBIDDEN");
        JsonNode done = ok(call(s.tech(), HttpMethod.POST, w + "/complete", Map.of("notes", "Replaced capacitor, recharged",
                "signedBy", "Dr. Chan"))).path("data");
        assertThat(done.path("summary").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(done.path("signedBy").asText()).isEqualTo("Dr. Chan");
        assertThat(done.path("completedBy").path("id").asText()).isEqualTo(s.tech().id().toString());
        assertThat(notificationTitles(s.dispatcher(), s.org())).contains("Work order completed: WO-0001");
        assertThat(code(call(s.dispatcher(), HttpMethod.PUT, w, workOrder(Map.of())))).isEqualTo("INVALID_STATE");
        // The asset's history shows it; the report counts it for the technician.
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/assets/" + a, null)).path("data").path("workOrders")
                .findValuesAsText("number")).containsExactly("WO-0001");
        JsonNode report = ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/reports", null)).path("data");
        assertThat(report.path("workOrdersCompleted").asInt()).isEqualTo(1);
        assertThat(dec(report.path("completedValue"))).isEqualTo("46.56");
        JsonNode row = report.path("technicians").get(0);
        assertThat(row.path("technician").path("id").asText()).isEqualTo(s.tech().id().toString());
        assertThat(row.path("labourHours").decimalValue()).isEqualByComparingTo("2.0");
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, w + "/activity", null)).path("data").findValuesAsText("verb"))
                .contains("service.workorder.created", "service.workorder.scheduled", "service.workorder.started",
                        "service.workorder.held", "service.workorder.checked", "service.workorder.completed");
    }

    @Test
    void work_orders_above_the_threshold_are_scheduled_only_after_approval() throws Exception {
        Setup s = setup();
        ok(call(s.owner(), HttpMethod.PUT, s.svc() + "/settings", Map.of("approvalThreshold", "500.00", "labourRate", "15.00")));
        Map<String, Object> big = workOrder(Map.of("title", "Replace compressor", "technicianIds", List.of(s.tech().id()),
                "lines", List.of(line("PART", "Compressor", "1", "560.00"), line("LABOUR", "Install", "4", "15.00"))));
        String id = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders", big)).path("data").path("summary").path("id").asText();
        String w = s.svc() + "/work-orders/" + id;
        JsonNode sent = ok(call(s.dispatcher(), HttpMethod.POST, w + "/schedule", null)).path("data");
        assertThat(sent.path("summary").path("status").asText()).isEqualTo("DRAFT");
        assertThat(sent.path("summary").path("approvalState").asText()).isEqualTo("PENDING");
        String approval = sent.path("approvalId").asText();
        // While it waits: no second request, no changes; the technician isn't told yet.
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, w + "/schedule", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.dispatcher(), HttpMethod.PUT, w + "/lines", Map.of("lines", List.of())))).isEqualTo("INVALID_STATE");
        assertThat(notificationTitles(s.tech(), s.org())).noneMatch(x -> x.startsWith("Work order for you"));
        // The Admins decide (not the dispatcher who asked).
        JsonNode inbox = ok(call(s.admin(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data");
        assertThat(inbox.findValuesAsText("id")).contains(approval);
        assertThat(inbox.findValuesAsText("title")).anyMatch(x -> x.startsWith("Work order WO-0001 · Replace compressor · $620.00"));
        assertThat(code(call(s.dispatcher(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve", Map.of())))
                .isEqualTo("FORBIDDEN");
        ok(call(s.admin(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approval + "/approve", Map.of()));
        JsonNode approved = ok(call(s.dispatcher(), HttpMethod.GET, w, null)).path("data");
        assertThat(approved.path("summary").path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(approved.path("summary").path("approvalState").asText()).isEqualTo("APPROVED");
        assertThat(dec(approved.path("approvedTotal"))).isEqualTo("620.00");
        assertThat(notificationTitles(s.tech(), s.org())).anyMatch(x -> x.startsWith("Work order for you: WO-0001"));

        // A rejected one stays a draft; under the threshold needs no approval at all.
        String second = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders", big)).path("data").path("summary").path("id").asText();
        String req = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders/" + second + "/schedule", null)).path("data")
                .path("approvalId").asText();
        ok(call(s.owner(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + req + "/reject", Map.of("comment", "Too expensive")));
        JsonNode rejected = ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/work-orders/" + second, null)).path("data");
        assertThat(rejected.path("summary").path("status").asText()).isEqualTo("DRAFT");
        assertThat(rejected.path("summary").path("approvalState").asText()).isEqualTo("REJECTED");
        ok(call(s.dispatcher(), HttpMethod.PUT, s.svc() + "/work-orders/" + second + "/lines",
                Map.of("lines", List.of(line("LABOUR", "Inspect only", "2", "15.00")))));
        assertThat(ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders/" + second + "/schedule", null)).path("data")
                .path("summary").path("status").asText()).isEqualTo("SCHEDULED");
        // Cancelling needs a reason; a cancelled one can't be started.
        ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/work-orders/" + second + "/cancel", Map.of("reason", "Customer moved")));
        assertThat(code(call(s.tech(), HttpMethod.POST, s.svc() + "/work-orders/" + second + "/start", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.tech2(), HttpMethod.POST, s.svc() + "/work-orders/" + second + "/start", null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void maintenance_plans_generate_their_next_work_order_once() throws Exception {
        Setup s = setup();
        String c = customer(s, "Riverside Mall");
        String a = asset(s, c, "SN-PM-1");
        JsonNode plan = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/maintenance", Map.of("assetId", a,
                "title", "Quarterly AC service", "intervalCount", 3, "intervalUnit", "MONTHS",
                "nextDue", TODAY.plusDays(3).toString(), "leadDays", 7, "technicianId", s.tech().id(),
                "checklist", List.of("Clean filters", "Check drain")))).path("data");
        assertThat(plan.path("generateOn").asText()).isEqualTo(TODAY.minusDays(4).toString());
        // Upcoming maintenance on the dispatcher's calendar.
        String cal = "/api/orgs/" + s.org() + "/calendar?from=" + TODAY + "&to=" + TODAY.plusDays(10);
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, cal, null)).path("data").path("deadlines").findValuesAsText("title"))
                .contains("Quarterly AC service");

        JsonNode made = ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/maintenance/generate", null)).path("data");
        assertThat(made.path("created").asInt()).isEqualTo(1);
        JsonNode wo = made.path("workOrders").get(0);
        assertThat(wo.path("preventive").asBoolean()).isTrue();
        assertThat(wo.path("status").asText()).isEqualTo("SCHEDULED"); // the plan's technician
        assertThat(wo.path("checklistTotal").asInt()).isEqualTo(2);
        assertThat(wo.path("technicians").findValuesAsText("id")).containsExactly(s.tech().id().toString());
        // Again, and the job: nothing new. The plan moved on one interval.
        assertThat(ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/maintenance/generate", null)).path("data")
                .path("created").asInt()).isZero();
        job.run();
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/work-orders?assetId=" + a, null)).path("data")
                .path("total").asInt()).isEqualTo(1);
        assertThat(ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/maintenance", null)).path("data").get(0)
                .path("nextDue").asText()).isEqualTo(TODAY.plusDays(3).plusMonths(3).toString());
        assertThat(notificationTitles(s.tech(), s.org())).anyMatch(x -> x.startsWith("Work order for you:"));

        // The job alone (no request): a second plan, due, is generated once even when run twice.
        ok(call(s.dispatcher(), HttpMethod.POST, s.svc() + "/maintenance", Map.of("assetId", a, "title", "Monthly filter check",
                "intervalCount", 30, "intervalUnit", "DAYS", "nextDue", TODAY.toString(), "leadDays", 0)));
        job.run();
        job.run();
        JsonNode list = ok(call(s.dispatcher(), HttpMethod.GET, s.svc() + "/work-orders?assetId=" + a, null)).path("data");
        assertThat(list.path("total").asInt()).isEqualTo(2);
        // No technician on the plan: a draft for the dispatcher to assign; made by the system.
        JsonNode draft = list.path("items").findParents("title").stream()
                .filter(n -> n.path("title").asText().equals("Monthly filter check")).findFirst().orElseThrow();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM svc_work_order WHERE plan_id IS NOT NULL AND org_id ="
                + " (SELECT id FROM organisation WHERE slug = ?)", Long.class, s.org())).isEqualTo(2L);
        // Technicians don't manage plans.
        assertThat(code(call(s.tech(), HttpMethod.POST, s.svc() + "/maintenance/generate", null))).isEqualTo("FORBIDDEN");
    }

    @Test
    void other_organisations_see_and_use_nothing() throws Exception {
        Setup a = setup();
        Setup b = setup();
        String c = customer(a, "Private Customer");
        String asset = asset(a, c, "SN-PRIVATE");
        String tk = ok(ticket(a, a.dispatcher(), Map.of("customerId", c))).path("data").path("summary").path("id").asText();
        String wo = ok(call(a.dispatcher(), HttpMethod.POST, a.svc() + "/work-orders", workOrder(Map.of("ticketId", tk))))
                .path("data").path("summary").path("id").asText();
        // Not a member: the organisation doesn't exist for them.
        assertThat(code(call(b.owner(), HttpMethod.GET, a.svc() + "/tickets", null))).isEqualTo("NOT_FOUND");
        // Through their own organisation, another's records are not found and can't be used.
        assertThat(code(call(b.owner(), HttpMethod.GET, b.svc() + "/tickets/" + tk, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.GET, b.svc() + "/work-orders/" + wo, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.POST, b.svc() + "/work-orders/" + wo + "/schedule", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.GET, b.svc() + "/assets/" + asset, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(b.owner(), HttpMethod.GET, b.svc() + "/customers/" + c, null))).isEqualTo("NOT_FOUND");
        assertThat(code(ticket(b, b.dispatcher(), Map.of("customerId", c)))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(b.dispatcher(), HttpMethod.POST, b.svc() + "/work-orders", workOrder(Map.of("ticketId", tk)))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(b.dispatcher(), HttpMethod.POST, b.svc() + "/work-orders",
                workOrder(Map.of("technicianIds", List.of(a.tech().id())))))).isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(b.owner(), HttpMethod.GET, b.svc() + "/tickets", null)).path("data").path("items")).isEmpty();
        assertThat(ok(call(b.owner(), HttpMethod.GET, b.svc() + "/work-orders", null)).path("data").path("items")).isEmpty();
        assertThat(ok(call(b.owner(), HttpMethod.GET, b.svc() + "/assets", null)).path("data").path("items")).isEmpty();
        // Numbers are per organisation.
        assertThat(ok(ticket(b, b.dispatcher(), Map.of())).path("data").path("summary").path("number").asText()).isEqualTo("TCK-0001");
    }
}
