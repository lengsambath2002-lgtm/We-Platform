package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * A two-level approval line, as companies run it: 1st manager, then the 2nd approver (set on the profile, else the
 * manager's manager); the same person twice approves once; the head of the company is approved at once.
 */
class ApprovalLineTest extends IntegrationTest {

    private record Org(String slug, User ceo, User lead, User member, User rdHead, User rdMember, String annual) {
        String hr() {
            return "/api/orgs/" + slug + "/hr";
        }
    }

    private Org company() throws Exception {
        User ceo = signUp("Line Ceo");
        User lead = signUp("Line Lead");
        User member = signUp("Line Member");
        User rdHead = signUp("Line Rd Head");
        User rdMember = signUp("Line Rd Member");
        String org = newOrg(ceo);
        for (User u : List.of(lead, member, rdHead, rdMember)) {
            join(ceo, org, u, "MEMBER");
        }
        ok(call(ceo, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String hr = "/api/orgs/" + org + "/hr";
        ok(call(ceo, HttpMethod.PUT, hr + "/people/" + lead.id(), new HashMap<>(Map.of("managerId", ceo.id()))));
        ok(call(ceo, HttpMethod.PUT, hr + "/people/" + member.id(), new HashMap<>(Map.of("managerId", lead.id()))));
        ok(call(ceo, HttpMethod.PUT, hr + "/people/" + rdHead.id(), new HashMap<>(Map.of("managerId", ceo.id()))));
        // In R&D the head is both the 1st and the 2nd approver.
        ok(call(ceo, HttpMethod.PUT, hr + "/people/" + rdMember.id(), new HashMap<>(Map.of("managerId", rdHead.id(), "secondManagerId", rdHead.id()))));
        ok(call(ceo, HttpMethod.POST, "/api/orgs/" + org + "/workflows", Map.of("name", "Manager sign-off", "moduleKey", "hr",
                "steps", List.of(Map.of("type", "MANAGER"), Map.of("type", "SECOND_MANAGER")))));
        String annual = null;
        for (JsonNode t : ok(call(ceo, HttpMethod.GET, hr + "/leave-types", null)).path("data")) {
            if (t.path("paid").asBoolean() && annual == null) {
                annual = t.path("id").asText();
            }
        }
        return new Org(org, ceo, lead, member, rdHead, rdMember, annual);
    }

    private JsonNode leave(Org o, User who, String day) throws Exception {
        return ok(call(who, HttpMethod.POST, o.hr() + "/leave", Map.of("leaveTypeId", o.annual(), "startDate", day, "endDate", day,
                "halfStart", false, "halfEnd", false, "reason", "Family"))).path("data");
    }

    private List<String> approvers(Org o, User who, JsonNode leave) throws Exception {
        List<String> out = new ArrayList<>();
        JsonNode a = ok(call(who, HttpMethod.GET, "/api/orgs/" + o.slug() + "/approvals/" + leave.path("approvalId").asText(), null)).path("data");
        a.path("steps").forEach(s -> out.add(s.path("approver").path("id").asText(s.path("role").asText())));
        return out;
    }

    @Test
    void a_member_goes_to_their_manager_then_the_managers_manager() throws Exception {
        Org o = company();
        JsonNode l = leave(o, o.member(), "2026-11-10");
        assertThat(approvers(o, o.member(), l)).containsExactly(o.lead().id().toString(), o.ceo().id().toString());

        String approval = "/api/orgs/" + o.slug() + "/approvals/" + l.path("approvalId").asText();
        // The 2nd approver can't jump the queue, and sees the request once it reaches them.
        assertThat(code(call(o.ceo(), HttpMethod.POST, approval + "/approve", Map.of()))).isEqualTo("FORBIDDEN");
        ok(call(o.lead(), HttpMethod.POST, approval + "/approve", Map.of()));
        assertThat(ok(call(o.ceo(), HttpMethod.GET, "/api/orgs/" + o.slug() + "/approvals?box=inbox", null)).path("data").findValuesAsText("title"))
                .anyMatch(t -> t.contains("Line Member"));
        ok(call(o.ceo(), HttpMethod.POST, approval + "/approve", Map.of()));
        assertThat(ok(call(o.member(), HttpMethod.GET, o.hr() + "/leave/" + l.path("id").asText(), null)).path("data").path("status").asText())
                .isEqualTo("APPROVED");

        // The profile shows the line.
        JsonNode p = ok(call(o.member(), HttpMethod.GET, o.hr() + "/people/" + o.member().id(), null)).path("data");
        assertThat(p.path("approvalLine").findValuesAsText("name")).containsExactly("Line Lead", "Line Ceo");
    }

    @Test
    void the_same_person_twice_approves_once_and_the_ceo_is_approved_at_once() throws Exception {
        Org o = company();
        // The R&D head is the 1st and the 2nd approver: one step.
        assertThat(approvers(o, o.rdMember(), leave(o, o.rdMember(), "2026-11-11"))).containsExactly(o.rdHead().id().toString());
        // A team lead reporting to the CEO: the CEO once (nobody above the CEO).
        assertThat(approvers(o, o.lead(), leave(o, o.lead(), "2026-11-12"))).containsExactly(o.ceo().id().toString());
        // The CEO (an Owner with nobody above): approved at once, not sent to other Admins.
        JsonNode top = leave(o, o.ceo(), "2026-11-13");
        assertThat(top.path("status").asText()).isEqualTo("APPROVED");
        JsonNode a = ok(call(o.ceo(), HttpMethod.GET, "/api/orgs/" + o.slug() + "/approvals/" + top.path("approvalId").asText(), null)).path("data");
        assertThat(a.path("steps")).isEmpty();
        assertThat(a.path("history").findValuesAsText("action")).contains("AUTO_APPROVE");
    }

    @Test
    void the_second_approver_is_checked_and_only_hr_workflows_use_it() throws Exception {
        Org o = company();
        assertThat(code(call(o.ceo(), HttpMethod.PUT, o.hr() + "/people/" + o.member().id(),
                new HashMap<>(Map.of("managerId", o.lead().id(), "secondManagerId", o.member().id()))))).isEqualTo("VALIDATION_FAILED");
        ok(call(o.ceo(), HttpMethod.PUT, "/api/orgs/" + o.slug() + "/modules/business", Map.of("enabled", true)));
        assertThat(code(call(o.ceo(), HttpMethod.POST, "/api/orgs/" + o.slug() + "/workflows", Map.of("name", "Expense line", "moduleKey", "business",
                "steps", List.of(Map.of("type", "SECOND_MANAGER")))))).isEqualTo("VALIDATION_FAILED");
    }
}
