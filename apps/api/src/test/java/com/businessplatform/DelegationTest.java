package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.businessplatform.workflow.service.ApprovalService;
import com.fasterxml.jackson.databind.JsonNode;

/** Acting approvers while someone is away, and reminders / escalation of requests that wait too long. */
class DelegationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApprovalService approvals;

    private record Org(String slug, User owner, User lead, User member, User standIn) {
        String api() {
            return "/api/orgs/" + slug;
        }
    }

    private Org company() throws Exception {
        User owner = signUp("Del Owner");
        User lead = signUp("Del Lead");
        User member = signUp("Del Member");
        User standIn = signUp("Del StandIn");
        String org = newOrg(owner);
        for (User u : List.of(lead, member, standIn)) {
            join(owner, org, u, "MEMBER");
        }
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/hr/people/" + member.id(), new HashMap<>(Map.of("managerId", lead.id()))));
        return new Org(org, owner, lead, member, standIn);
    }

    private static LocalDate today() {
        return LocalDate.now(CalendarService.ZONE);
    }

    private static LocalDate pastWeekday() {
        LocalDate d = today().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }

    private JsonNode overtime(Org o) throws Exception {
        return ok(call(o.member(), HttpMethod.POST, o.api() + "/hr/overtime",
                Map.of("workDate", pastWeekday().toString(), "minutes", 90, "reason", "Release night"))).path("data");
    }

    private List<String> inbox(Org o, User who) throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        ok(call(who, HttpMethod.GET, o.api() + "/approvals?box=inbox", null)).path("data").forEach(a -> ids.add(a.path("id").asText()));
        return ids;
    }

    @Test
    void an_acting_approver_decides_while_the_approver_is_away() throws Exception {
        Org o = company();
        String del = o.api() + "/approvals/delegations";
        Map<String, Object> away = new HashMap<>(Map.of("delegateId", o.standIn().id(), "startsOn", today().toString(),
                "endsOn", today().plusDays(5).toString(), "reason", "Business trip"));
        // Only for oneself, unless an Admin or HR manager; never to oneself; not twice for the same days.
        Map<String, Object> forLead = new HashMap<>(away);
        forLead.put("userId", o.lead().id());
        assertThat(code(call(o.member(), HttpMethod.POST, del, forLead))).isEqualTo("FORBIDDEN");
        Map<String, Object> self = new HashMap<>(away);
        self.put("delegateId", o.lead().id());
        assertThat(code(call(o.lead(), HttpMethod.POST, del, self))).isEqualTo("VALIDATION_FAILED");
        String delegation = ok(call(o.lead(), HttpMethod.POST, del, away)).path("data").path("id").asText();
        assertThat(code(call(o.owner(), HttpMethod.POST, del, forLead))).isEqualTo("CONFLICT");

        // Overtime is private to the person and their approvers: the stand-in sees it as the lead does.
        JsonNode ot = overtime(o);
        String approval = ot.path("approvalId").asText();
        assertThat(inbox(o, o.standIn())).contains(approval);
        JsonNode d = ok(call(o.standIn(), HttpMethod.GET, o.api() + "/approvals/" + approval, null)).path("data");
        assertThat(d.path("canDecide").asBoolean()).isTrue();
        assertThat(d.path("steps").get(0).path("delegate").path("name").asText()).isEqualTo("Del StandIn");
        ok(call(o.standIn(), HttpMethod.POST, o.api() + "/approvals/" + approval + "/approve", Map.of()));
        d = ok(call(o.member(), HttpMethod.GET, o.api() + "/approvals/" + approval, null)).path("data");
        assertThat(d.path("summary").path("status").asText()).isEqualTo("APPROVED");
        assertThat(d.path("steps").get(0).path("actingFor").path("name").asText()).isEqualTo("Del Lead");
        assertThat(d.path("history").get(1).path("actingFor").path("name").asText()).isEqualTo("Del Lead");

        // Ended early: the stand-in no longer acts.
        assertThat(ok(call(o.standIn(), HttpMethod.GET, del, null)).path("data").path("mine")).hasSize(1);
        ok(call(o.lead(), HttpMethod.DELETE, del + "/" + delegation, null));
        String next = ok(call(o.member(), HttpMethod.POST, o.api() + "/hr/leave", Map.of("leaveTypeId",
                ok(call(o.owner(), HttpMethod.GET, o.api() + "/hr/leave-types", null)).path("data").get(0).path("id").asText(),
                "startDate", "2026-12-08", "endDate", "2026-12-08", "halfStart", false, "halfEnd", false))).path("data").path("approvalId").asText();
        assertThat(inbox(o, o.standIn())).doesNotContain(next);
        assertThat(code(call(o.standIn(), HttpMethod.POST, o.api() + "/approvals/" + next + "/approve", Map.of()))).isIn("FORBIDDEN", "NOT_FOUND");
    }

    @Test
    void a_waiting_request_is_reminded_then_escalated_to_the_admins() throws Exception {
        Org o = company();
        JsonNode wf = null;
        for (JsonNode w : ok(call(o.owner(), HttpMethod.GET, o.api() + "/workflows", null)).path("data")) {
            if (w.path("moduleKey").asText().equals("hr")) {
                wf = w;
            }
        }
        JsonNode ot = overtime(o);
        if (wf == null) {
            for (JsonNode w : ok(call(o.owner(), HttpMethod.GET, o.api() + "/workflows", null)).path("data")) {
                if (w.path("moduleKey").asText().equals("hr")) {
                    wf = w;
                }
            }
        }
        assertThat(code(call(o.owner(), HttpMethod.PUT, o.api() + "/workflows/" + wf.path("id").asText(), Map.of("name", wf.path("name").asText(),
                "moduleKey", "hr", "steps", List.of(Map.of("type", "MANAGER")), "remindAfterHours", 24, "escalateAfterHours", 12))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(o.owner(), HttpMethod.PUT, o.api() + "/workflows/" + wf.path("id").asText(), Map.of("name", wf.path("name").asText(),
                "moduleKey", "hr", "steps", List.of(Map.of("type", "MANAGER")), "remindAfterHours", 24, "escalateAfterHours", 72)));
        String approval = ot.path("approvalId").asText();
        String step = "UPDATE approval_step SET activated_at = now() - make_interval(hours => ?) WHERE request_id = ?::uuid";

        jdbc.update(step, 25, approval);
        approvals.remindAndEscalate();
        assertThat(ok(call(o.lead(), HttpMethod.GET, o.api() + "/notifications", null)).path("data").path("items").findValuesAsText("type"))
                .contains("approval.reminder");
        assertThat(inbox(o, o.owner())).doesNotContain(approval);

        jdbc.update(step, 73, approval);
        approvals.remindAndEscalate();
        assertThat(ok(call(o.owner(), HttpMethod.GET, o.api() + "/notifications", null)).path("data").path("items").findValuesAsText("type"))
                .contains("approval.escalated");
        assertThat(inbox(o, o.owner())).contains(approval);
        ok(call(o.owner(), HttpMethod.POST, o.api() + "/approvals/" + approval + "/approve", Map.of()));
        JsonNode d = ok(call(o.member(), HttpMethod.GET, o.api() + "/approvals/" + approval, null)).path("data");
        assertThat(d.path("summary").path("status").asText()).isEqualTo("APPROVED");
        assertThat(d.path("steps").get(0).path("escalated").asBoolean()).isTrue();
        assertThat(d.path("steps").get(0).path("actingFor").path("name").asText()).isEqualTo("Del Lead");
    }
}
