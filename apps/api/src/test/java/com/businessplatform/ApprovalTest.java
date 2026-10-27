package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Journey 5: request approval on a task; approve / reject / request changes; history and notifications follow. */
class ApprovalTest extends IntegrationTest {

    private record Setup(User lead, User member, User admin, String org, String taskId) {
    }

    private Setup setup() throws Exception {
        User admin = signUp("Org Admin");
        User lead = signUp("Proj Lead");
        User member = signUp("Proj Member");
        String org = newOrg(admin);
        join(admin, org, lead, "MEMBER");
        join(admin, org, member, "MEMBER");
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Budget", "key", "BUD",
                "members", List.of(Map.of("userId", member.id(), "role", "MEMBER")))));
        String taskId = ok(call(member, HttpMethod.POST, "/api/orgs/" + org + "/projects/BUD/tasks",
                Map.of("title", "Buy laptops"))).path("data").path("id").asText();
        return new Setup(lead, member, admin, org, taskId);
    }

    private String workflow(User u, String org, String name) throws Exception {
        for (JsonNode w : ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/workflows", null)).path("data")) {
            if (w.path("name").asText().equals(name)) {
                return w.path("id").asText();
            }
        }
        throw new AssertionError(name);
    }

    private JsonNode submit(Setup s, String workflowId) throws Exception {
        return call(s.member(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals", Map.of("workflowId", workflowId,
                "moduleKey", "projects", "recordType", "task", "recordId", s.taskId(), "summary", "3 laptops, $2,400"));
    }

    @Test
    void request_changes_then_resubmit_then_approve_updates_the_task_history() throws Exception {
        Setup s = setup();
        String leadFlow = workflow(s.member(), s.org(), "Project lead sign-off");
        JsonNode req = ok(submit(s, leadFlow)).path("data");
        String id = req.path("summary").path("id").asText();
        assertThat(req.path("summary").path("status").asText()).isEqualTo("PENDING");

        // One open request per record; the lead sees it in their inbox, the requester can't decide it.
        assertThat(code(submit(s, leadFlow))).isEqualTo("CONFLICT");
        assertThat(ok(call(s.lead(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data")).hasSize(1);
        assertThat(code(call(s.member(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of())))
                .isEqualTo("FORBIDDEN");

        // Request changes needs a reason, then the requester resubmits, then the lead approves.
        assertThat(code(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/request-changes",
                Map.of()))).isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/request-changes",
                Map.of("comment", "Add the quotes"))).path("data").path("summary").path("status").asText())
                .isEqualTo("CHANGES_REQUESTED");
        assertThat(code(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of())))
                .isEqualTo("INVALID_STATE");
        ok(call(s.member(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/resubmit", Map.of("comment", "Quotes attached")));
        JsonNode done = ok(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve",
                Map.of("comment", "OK"))).path("data");
        assertThat(done.path("summary").path("status").asText()).isEqualTo("APPROVED");
        assertThat(done.path("history").findValuesAsText("action"))
                .containsExactly("SUBMIT", "REQUEST_CHANGES", "RESUBMIT", "APPROVE");

        List<String> verbs = ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/tasks/" + s.taskId() + "/activity", null))
                .path("data").findValuesAsText("verb");
        assertThat(verbs).contains("approval.requested", "approval.changes_requested", "approval.resubmitted", "approval.approved");
        List<String> types = ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null))
                .path("data").path("items").findValuesAsText("type");
        assertThat(types).contains("approval.changes_requested", "approval.approved");
    }

    @Test
    void admin_route_reject_and_self_approval_is_refused() throws Exception {
        Setup s = setup();
        // The lead requesting on a lead route would approve their own request.
        String leadTask = ok(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/projects/BUD/tasks",
                Map.of("title", "Lead's own"))).path("data").path("id").asText();
        assertThat(code(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals",
                Map.of("workflowId", workflow(s.lead(), s.org(), "Project lead sign-off"), "moduleKey", "projects",
                        "recordType", "task", "recordId", leadTask)))).isEqualTo("VALIDATION_FAILED");

        String id = ok(submit(s, workflow(s.member(), s.org(), "Admin sign-off"))).path("data").path("summary").path("id").asText();
        assertThat(code(call(s.lead(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of())))
                .isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.admin(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/reject",
                Map.of("comment", "No budget"))).path("data").path("summary").path("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void someone_who_cannot_see_the_record_cannot_see_or_decide_its_approval() throws Exception {
        Setup s = setup();
        ok(call(s.lead(), HttpMethod.PATCH, "/api/orgs/" + s.org() + "/projects/BUD", Map.of("visibility", "PRIVATE")));
        User stranger = signUp("Stranger");
        join(s.admin(), s.org(), stranger, "MEMBER");
        // A named-approver route pointing at someone who can't see the project.
        String flow = ok(call(s.admin(), HttpMethod.POST, "/api/orgs/" + s.org() + "/workflows", Map.of("name", "Stranger route",
                "moduleKey", "projects", "steps", List.of(Map.of("type", "USER", "userId", stranger.id()))))).path("data").path("id").asText();
        String id = ok(submit(s, flow)).path("data").path("summary").path("id").asText();
        assertThat(ok(call(stranger, HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data")).isEmpty();
        assertThat(code(call(stranger, HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals/" + id, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(stranger, HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of())))
                .isEqualTo("NOT_FOUND");
        assertThat(ok(call(s.member(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/withdraw", null))
                .path("data").path("summary").path("status").asText()).isEqualTo("WITHDRAWN");
    }
}
