package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Journeys 3–4: a manager creates a project and assigns tasks; members update, comment and attach. */
class ProjectTaskTest extends IntegrationTest {

    @Test
    void manager_creates_a_project_assigns_tasks_and_members_work_on_them() throws Exception {
        User lead = signUp("Lead Person");
        User dev = signUp("Dev Person");
        User viewer = signUp("Viewer Person");
        String org = newOrg(lead);
        join(lead, org, dev, "MEMBER");
        join(lead, org, viewer, "MEMBER");
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Launch", "key", "LCH",
                "visibility", "PRIVATE", "members", List.of(Map.of("userId", dev.id(), "role", "MEMBER"),
                        Map.of("userId", viewer.id(), "role", "VIEWER")))));

        JsonNode t = ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/LCH/tasks",
                Map.of("title", "Write copy", "assigneeId", dev.id(), "dueDate", "2026-11-10", "priority", "HIGH")));
        String id = t.path("data").path("id").asText();
        assertThat(t.path("data").path("key").asText()).isEqualTo("LCH-1");
        // The assignee is told; it is on their My Tasks.
        assertThat(ok(call(dev, HttpMethod.GET, "/api/orgs/" + org + "/notifications", null)).path("data")
                .path("unread").asInt()).isEqualTo(2); // added to project + assigned
        assertThat(ok(call(dev, HttpMethod.GET, "/api/orgs/" + org + "/my-tasks", null)).path("data")).hasSize(1);

        // Viewers can't be assigned or edit; they can read and comment.
        assertThat(code(call(lead, HttpMethod.PATCH, "/api/orgs/" + org + "/tasks/" + id,
                Map.of("assigneeId", viewer.id())))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(viewer, HttpMethod.PATCH, "/api/orgs/" + org + "/tasks/" + id, Map.of("title", "x"))))
                .isEqualTo("FORBIDDEN");
        ok(call(viewer, HttpMethod.POST, "/api/orgs/" + org + "/tasks/" + id + "/comments",
                Map.of("body", "Looks good @Lead", "mentionIds", List.of(lead.id()))));

        // The member moves it on the board and attaches a file; history keeps every step.
        ok(call(dev, HttpMethod.POST, "/api/orgs/" + org + "/tasks/" + id + "/move", Map.of("status", "IN_PROGRESS", "index", 0)));
        mvc.perform(multipart("/api/orgs/" + org + "/tasks/" + id + "/attachments")
                .file(new MockMultipartFile("file", "brief.pdf", "application/pdf", "%PDF-1.4 test".getBytes()))
                .header("Authorization", "Bearer " + dev.token())).andReturn();
        JsonNode detail = ok(call(dev, HttpMethod.GET, "/api/orgs/" + org + "/tasks/" + id, null)).path("data");
        assertThat(detail.path("task").path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(detail.path("attachments")).hasSize(1);
        String fileId = detail.path("attachments").get(0).path("id").asText();
        assertThat(mvc.perform(get("/api/orgs/" + org + "/tasks/" + id + "/attachments/" + fileId)
                .header("Authorization", "Bearer " + viewer.token())).andReturn().getResponse().getContentAsString())
                .isEqualTo("%PDF-1.4 test");
        List<String> verbs = ok(call(lead, HttpMethod.GET, "/api/orgs/" + org + "/tasks/" + id + "/activity", null))
                .path("data").findValuesAsText("verb");
        assertThat(verbs).containsExactly("task.created", "task.commented", "task.updated", "task.attached");
    }

    @Test
    void a_private_project_is_invisible_to_non_members_and_guests_create_nothing() throws Exception {
        User lead = signUp("Private Lead");
        User member = signUp("Not Invited");
        User guest = signUp("Guest User");
        String org = newOrg(lead);
        join(lead, org, member, "MEMBER");
        join(lead, org, guest, "GUEST");
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Hidden", "key", "HID", "visibility", "PRIVATE")));
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Open", "key", "OPN")));
        assertThat(ok(call(member, HttpMethod.GET, "/api/orgs/" + org + "/projects", null)).path("data")
                .findValuesAsText("key")).containsExactly("OPN");
        assertThat(code(call(member, HttpMethod.GET, "/api/orgs/" + org + "/projects/HID", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(guest, HttpMethod.GET, "/api/orgs/" + org + "/projects", null)).path("data")).isEmpty();
        assertThat(code(call(guest, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Nope", "key", "NOP"))))
                .isEqualTo("FORBIDDEN");
        // ORG projects: any member may view but only project members edit.
        JsonNode t = ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/OPN/tasks", Map.of("title", "One")));
        assertThat(code(call(member, HttpMethod.PATCH, "/api/orgs/" + org + "/tasks/" + t.path("data").path("id").asText(),
                Map.of("title", "Two")))).isEqualTo("FORBIDDEN");
    }

    @Test
    void the_board_keeps_order_and_subtasks_roll_up() throws Exception {
        User lead = signUp("Board Lead");
        String org = newOrg(lead);
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Board", "key", "BRD")));
        String a = ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/BRD/tasks", Map.of("title", "A"))).path("data").path("id").asText();
        String b = ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/BRD/tasks", Map.of("title", "B"))).path("data").path("id").asText();
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/tasks/" + b + "/move", Map.of("status", "TODO", "index", 0)));
        assertThat(ok(call(lead, HttpMethod.GET, "/api/orgs/" + org + "/projects/BRD/tasks", null)).path("data")
                .findValuesAsText("title")).containsExactly("B", "A");
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/BRD/tasks", Map.of("title", "A.1", "parentId", a)));
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/BRD/tasks", Map.of("title", "A.2", "parentId", a, "status", "DONE")));
        JsonNode top = ok(call(lead, HttpMethod.GET, "/api/orgs/" + org + "/tasks/" + a, null)).path("data");
        assertThat(top.path("task").path("subtasks").asInt()).isEqualTo(2);
        assertThat(top.path("task").path("subtasksDone").asInt()).isEqualTo(1);
        assertThat(code(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects/BRD/tasks",
                Map.of("title", "x", "dueDate", "2026-01-01", "startDate", "2026-02-01")))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void the_lead_changes_members_and_roles_and_removed_people_lose_a_private_project() throws Exception {
        User lead = signUp("Team Lead");
        User dev = signUp("Team Dev");
        User other = signUp("Team Other");
        String org = newOrg(lead);
        join(lead, org, dev, "MEMBER");
        join(lead, org, other, "MEMBER");
        String path = "/api/orgs/" + org + "/projects/TM";
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Team", "key", "TM",
                "visibility", "PRIVATE", "members", List.of(Map.of("userId", other.id(), "role", "MEMBER")))));

        // Add dev, remove other; then change dev's role on the kept row.
        JsonNode saved = ok(call(lead, HttpMethod.PUT, path + "/members", List.of(Map.of("userId", lead.id(), "role", "LEAD"),
                Map.of("userId", dev.id(), "role", "VIEWER")))).path("data");
        assertThat(saved).hasSize(2);
        saved = ok(call(lead, HttpMethod.PUT, path + "/members", List.of(Map.of("userId", lead.id(), "role", "LEAD"),
                Map.of("userId", dev.id(), "role", "LEAD")))).path("data");
        assertThat(saved.findValuesAsText("role")).containsOnly("LEAD");
        assertThat(code(call(other, HttpMethod.GET, path, null))).isEqualTo("NOT_FOUND");

        // A list without a lead is refused; the old list stays.
        assertThat(code(call(dev, HttpMethod.PUT, path + "/members", List.of(Map.of("userId", dev.id(), "role", "MEMBER")))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(dev, HttpMethod.GET, path + "/members", null)).path("data")).hasSize(2);
    }
}
