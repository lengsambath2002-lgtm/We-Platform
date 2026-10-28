package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Collaboration: comment edits, archived and deleted projects, event owners, the notifications page, the audit log. */
class CollabTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private record Team(User admin, User lead, User member, User viewer, String org, String taskId) {
    }

    /** An org (admin), a project CLB led by {@code lead} with a member and a viewer, and one task. */
    private Team team() throws Exception {
        User admin = signUp("Collab Admin");
        User lead = signUp("Collab Lead");
        User member = signUp("Collab Member");
        User viewer = signUp("Collab Viewer");
        String org = newOrg(admin);
        join(admin, org, lead, "MEMBER");
        join(admin, org, member, "MEMBER");
        join(admin, org, viewer, "MEMBER");
        ok(call(lead, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Collab", "key", "CLB",
                "visibility", "PRIVATE", "members", List.of(Map.of("userId", member.id(), "role", "MEMBER"),
                        Map.of("userId", viewer.id(), "role", "VIEWER")))));
        String taskId = ok(call(member, HttpMethod.POST, "/api/orgs/" + org + "/projects/CLB/tasks",
                Map.of("title", "Draft plan"))).path("data").path("id").asText();
        return new Team(admin, lead, member, viewer, org, taskId);
    }

    private static String comments(Team s) {
        return "/api/orgs/" + s.org() + "/tasks/" + s.taskId() + "/comments";
    }

    private long mentions(User u, String org) throws Exception {
        long n = 0;
        for (JsonNode i : ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/notifications?size=100", null)).path("data")
                .path("items")) {
            n += "task.mentioned".equals(i.path("type").asText()) ? 1 : 0;
        }
        return n;
    }

    @Test
    void the_author_edits_a_comment_and_the_author_lead_or_admin_delete_it() throws Exception {
        Team s = team();
        JsonNode c = ok(call(s.viewer(), HttpMethod.POST, comments(s),
                Map.of("body", "Ping @Collab Member", "mentionIds", List.of(s.member().id())))).path("data");
        String id = c.path("id").asText();
        assertThat(c.path("canEdit").asBoolean()).isTrue();
        assertThat(mentions(s.member(), s.org())).isEqualTo(1);

        // Someone else can't edit it; the author can, and it is marked edited.
        assertThat(code(call(s.member(), HttpMethod.PATCH, comments(s) + "/" + id, Map.of("body", "Hijack"))))
                .isEqualTo("FORBIDDEN");
        assertThat(code(call(s.lead(), HttpMethod.PATCH, comments(s) + "/" + id, Map.of("body", "Hijack"))))
                .isEqualTo("FORBIDDEN");
        JsonNode edited = ok(call(s.viewer(), HttpMethod.PATCH, comments(s) + "/" + id,
                Map.of("body", "Ping @Collab Member and @Collab Lead", "mentionIds", List.of(s.member().id(), s.lead().id()))))
                .path("data");
        assertThat(edited.path("editedAt").isNull()).isFalse();
        // Only the newly mentioned person is notified again.
        assertThat(mentions(s.member(), s.org())).isEqualTo(1);
        assertThat(mentions(s.lead(), s.org())).isEqualTo(1);

        // Another member sees it but may not delete it; the viewer sees canEdit only on their own.
        JsonNode asMember = ok(call(s.member(), HttpMethod.GET, comments(s), null)).path("data").get(0);
        assertThat(asMember.path("canEdit").asBoolean()).isFalse();
        assertThat(asMember.path("canDelete").asBoolean()).isFalse();
        assertThat(code(call(s.member(), HttpMethod.DELETE, comments(s) + "/" + id, null))).isEqualTo("FORBIDDEN");
        // The project lead may delete it.
        assertThat(ok(call(s.lead(), HttpMethod.GET, comments(s), null)).path("data").get(0).path("canDelete").asBoolean())
                .isTrue();
        ok(call(s.lead(), HttpMethod.DELETE, comments(s) + "/" + id, null));
        assertThat(ok(call(s.lead(), HttpMethod.GET, comments(s), null)).path("data")).isEmpty();
        assertThat(code(call(s.lead(), HttpMethod.DELETE, comments(s) + "/" + id, null))).isEqualTo("NOT_FOUND");

        // An org admin may delete anyone's; the author their own.
        String second = ok(call(s.member(), HttpMethod.POST, comments(s), Map.of("body", "Second"))).path("data")
                .path("id").asText();
        ok(call(s.admin(), HttpMethod.DELETE, comments(s) + "/" + second, null));
        String third = ok(call(s.member(), HttpMethod.POST, comments(s), Map.of("body", "Third"))).path("data")
                .path("id").asText();
        ok(call(s.member(), HttpMethod.DELETE, comments(s) + "/" + third, null));

        List<String> verbs = ok(call(s.lead(), HttpMethod.GET, "/api/orgs/" + s.org() + "/tasks/" + s.taskId() + "/activity",
                null)).path("data").findValuesAsText("verb");
        assertThat(verbs).contains("task.comment.edited", "task.comment.deleted");
    }

    @Test
    void an_archived_project_refuses_task_changes_until_restored() throws Exception {
        Team s = team();
        String base = "/api/orgs/" + s.org();
        ok(call(s.lead(), HttpMethod.PATCH, base + "/projects/CLB", Map.of("status", "ARCHIVED")));
        JsonNode p = ok(call(s.member(), HttpMethod.GET, base + "/projects/CLB", null)).path("data");
        assertThat(p.path("canEdit").asBoolean()).isFalse();
        assertThat(ok(call(s.lead(), HttpMethod.GET, base + "/projects/CLB", null)).path("data").path("canManage")
                .asBoolean()).isTrue();

        assertThat(code(call(s.member(), HttpMethod.POST, base + "/projects/CLB/tasks", Map.of("title", "New"))))
                .isEqualTo("INVALID_STATE");
        assertThat(code(call(s.member(), HttpMethod.PATCH, base + "/tasks/" + s.taskId(), Map.of("title", "Renamed"))))
                .isEqualTo("INVALID_STATE");
        assertThat(code(call(s.lead(), HttpMethod.POST, base + "/tasks/" + s.taskId() + "/move",
                Map.of("status", "DONE", "index", 0)))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.admin(), HttpMethod.DELETE, base + "/tasks/" + s.taskId(), null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.member(), HttpMethod.POST, comments(s), Map.of("body", "Hello"))))
                .isEqualTo("INVALID_STATE");
        JsonNode detail = ok(call(s.member(), HttpMethod.GET, base + "/tasks/" + s.taskId(), null)).path("data");
        assertThat(detail.path("canEdit").asBoolean()).isFalse();
        assertThat(detail.path("canComment").asBoolean()).isFalse();

        // Restored, work goes on.
        ok(call(s.lead(), HttpMethod.PATCH, base + "/projects/CLB", Map.of("status", "ACTIVE")));
        ok(call(s.member(), HttpMethod.PATCH, base + "/tasks/" + s.taskId(), Map.of("title", "Renamed")));
    }

    @Test
    void deleting_a_project_removes_its_tasks_approvals_and_files() throws Exception {
        Team s = team();
        String base = "/api/orgs/" + s.org();
        String workflowId = null;
        for (JsonNode w : ok(call(s.member(), HttpMethod.GET, base + "/workflows", null)).path("data")) {
            if (w.path("name").asText().equals("Project lead sign-off")) {
                workflowId = w.path("id").asText();
            }
        }
        String approvalId = ok(call(s.member(), HttpMethod.POST, base + "/approvals", Map.of("workflowId", workflowId,
                "moduleKey", "projects", "recordType", "task", "recordId", s.taskId(), "summary", "Sign off")))
                .path("data").path("summary").path("id").asText();
        mvc.perform(multipart(base + "/tasks/" + s.taskId() + "/attachments")
                .file(new MockMultipartFile("file", "plan.pdf", "application/pdf", "%PDF-1.4 plan".getBytes()))
                .header("Authorization", "Bearer " + s.member().token())).andReturn();
        String fileId = ok(call(s.member(), HttpMethod.GET, base + "/tasks/" + s.taskId(), null)).path("data")
                .path("attachments").get(0).path("id").asText();
        ok(call(s.member(), HttpMethod.POST, comments(s), Map.of("body", "Attached")));

        // Members can't delete; the lead can.
        assertThat(code(call(s.member(), HttpMethod.DELETE, base + "/projects/CLB", null))).isEqualTo("FORBIDDEN");
        ok(call(s.lead(), HttpMethod.DELETE, base + "/projects/CLB", null));

        assertThat(code(call(s.lead(), HttpMethod.GET, base + "/projects/CLB", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.lead(), HttpMethod.GET, base + "/tasks/" + s.taskId(), null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.lead(), HttpMethod.GET, base + "/approvals/" + approvalId, null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(s.lead(), HttpMethod.GET, base + "/approvals?box=inbox", null)).path("data")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_request WHERE id = ?", Long.class,
                UUID.fromString(approvalId))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM stored_file WHERE id = ?", Long.class,
                UUID.fromString(fileId))).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_comment WHERE task_id = ?", Long.class,
                UUID.fromString(s.taskId()))).isZero();
        // The deletion itself is in the audit log.
        assertThat(ok(call(s.admin(), HttpMethod.GET, base + "/activity?recordType=project", null)).path("data")
                .path("items").findValuesAsText("verb")).contains("project.deleted");
    }

    @Test
    void only_the_owner_edits_an_event_and_attendees_see_it_read_only() throws Exception {
        Team s = team();
        String base = "/api/orgs/" + s.org();
        Map<String, Object> ev = Map.of("title", "Kick-off", "startsAt", "2026-11-03T02:00:00Z", "endsAt",
                "2026-11-03T03:00:00Z", "allDay", false, "attendeeIds", List.of(s.member().id()));
        JsonNode e = ok(call(s.lead(), HttpMethod.POST, base + "/events", ev)).path("data");
        String id = e.path("id").asText();
        assertThat(e.path("canEdit").asBoolean()).isTrue();

        JsonNode seen = ok(call(s.member(), HttpMethod.GET, base + "/calendar?from=2026-11-01&to=2026-11-30", null))
                .path("data").path("events").get(0);
        assertThat(seen.path("canEdit").asBoolean()).isFalse();
        assertThat(seen.path("canDelete").asBoolean()).isFalse();
        Map<String, Object> changed = Map.of("title", "Kick-off (moved)", "startsAt", "2026-11-04T02:00:00Z", "endsAt",
                "2026-11-04T03:00:00Z", "allDay", false, "attendeeIds", List.of(s.member().id(), s.viewer().id()));
        assertThat(code(call(s.member(), HttpMethod.PUT, base + "/events/" + id, changed))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.admin(), HttpMethod.PUT, base + "/events/" + id, changed))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.member(), HttpMethod.DELETE, base + "/events/" + id, null))).isEqualTo("FORBIDDEN");

        // The owner moves it and invites one more: only the newcomer is notified.
        ok(call(s.lead(), HttpMethod.PUT, base + "/events/" + id, changed));
        long invites = 0;
        for (JsonNode i : ok(call(s.member(), HttpMethod.GET, base + "/notifications", null)).path("data").path("items")) {
            invites += "event.invited".equals(i.path("type").asText()) ? 1 : 0;
        }
        assertThat(invites).isEqualTo(1);
        assertThat(ok(call(s.viewer(), HttpMethod.GET, base + "/notifications?unread=true", null)).path("data")
                .path("items").findValuesAsText("type")).contains("event.invited");

        // The project link must be a project the owner can see.
        assertThat(code(call(s.lead(), HttpMethod.PUT, base + "/events/" + id, Map.of("title", "x", "startsAt",
                "2026-11-04T02:00:00Z", "endsAt", "2026-11-04T03:00:00Z", "allDay", false, "projectId",
                UUID.randomUUID())))).isEqualTo("VALIDATION_FAILED");

        // An admin may tidy it away.
        ok(call(s.admin(), HttpMethod.DELETE, base + "/events/" + id, null));
    }

    @Test
    void notifications_page_filter_and_read_and_preferences() throws Exception {
        Team s = team();
        String base = "/api/orgs/" + s.org();
        for (int i = 0; i < 5; i++) {
            ok(call(s.lead(), HttpMethod.POST, base + "/projects/CLB/tasks",
                    Map.of("title", "Task " + i, "assigneeId", s.member().id())));
        }
        // member: added to the project + 5 assignments
        JsonNode first = ok(call(s.member(), HttpMethod.GET, base + "/notifications?page=0&size=4", null)).path("data");
        assertThat(first.path("items")).hasSize(4);
        assertThat(first.path("total").asLong()).isEqualTo(6);
        assertThat(first.path("unread").asLong()).isEqualTo(6);
        JsonNode second = ok(call(s.member(), HttpMethod.GET, base + "/notifications?page=1&size=4", null)).path("data");
        assertThat(second.path("items")).hasSize(2);
        assertThat(second.path("items").findValuesAsText("id")).doesNotContainAnyElementsOf(
                first.path("items").findValuesAsText("id"));

        ok(call(s.member(), HttpMethod.POST, base + "/notifications/read?id=" + first.path("items").get(0).path("id").asText(),
                null));
        JsonNode unread = ok(call(s.member(), HttpMethod.GET, base + "/notifications?unread=true", null)).path("data");
        assertThat(unread.path("total").asLong()).isEqualTo(5);
        assertThat(unread.path("unread").asLong()).isEqualTo(5);
        ok(call(s.member(), HttpMethod.POST, base + "/notifications/read", null));
        assertThat(ok(call(s.member(), HttpMethod.GET, base + "/notifications?unread=true", null)).path("data")
                .path("items")).isEmpty();
        // Someone else's notification can't be read for them.
        assertThat(ok(call(s.lead(), HttpMethod.GET, base + "/notifications", null)).path("data").path("items")).isEmpty();

        // Switching a kind off stops it, in this organisation only.
        JsonNode prefs = ok(call(s.member(), HttpMethod.GET, base + "/notifications/preferences", null)).path("data");
        assertThat(prefs.path("kinds").findValuesAsText("kind")).contains("task.assigned", "task.mentioned");
        assertThat(code(call(s.member(), HttpMethod.PUT, base + "/notifications/preferences",
                Map.of("muted", List.of("nonsense"))))).isEqualTo("VALIDATION_FAILED");
        ok(call(s.member(), HttpMethod.PUT, base + "/notifications/preferences", Map.of("muted", List.of("task.assigned"))));
        ok(call(s.lead(), HttpMethod.POST, base + "/projects/CLB/tasks", Map.of("title", "Quiet", "assigneeId", s.member().id())));
        assertThat(ok(call(s.member(), HttpMethod.GET, base + "/notifications", null)).path("data").path("total").asLong())
                .isEqualTo(6);
    }

    @Test
    void the_audit_log_is_for_admins_and_stays_in_its_organisation() throws Exception {
        Team s = team();
        String base = "/api/orgs/" + s.org();
        ok(call(s.member(), HttpMethod.PATCH, base + "/tasks/" + s.taskId(), Map.of("status", "IN_PROGRESS")));
        ok(call(s.admin(), HttpMethod.PUT, base + "/modules/projects", Map.of("enabled", true)));

        assertThat(code(call(s.lead(), HttpMethod.GET, base + "/activity", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.member(), HttpMethod.GET, base + "/activity", null))).isEqualTo("FORBIDDEN");

        JsonNode all = ok(call(s.admin(), HttpMethod.GET, base + "/activity?size=100", null)).path("data");
        List<String> verbs = all.path("items").findValuesAsText("verb");
        assertThat(verbs).contains("project.created", "task.created", "task.updated", "org.member.joined",
                "org.member.invited");
        // Newest first; the task row is named and linked.
        JsonNode taskRow = null;
        for (JsonNode r : all.path("items")) {
            if ("task.updated".equals(r.path("verb").asText())) {
                taskRow = r;
            }
        }
        assertThat(taskRow.path("record").path("title").asText()).isEqualTo("CLB-1 Draft plan");
        assertThat(taskRow.path("record").path("link").asText()).endsWith("/tasks/" + s.taskId());
        assertThat(taskRow.path("actor").path("name").asText()).isEqualTo("Collab Member");

        // Filters: person, record type, verb prefix, dates, paging.
        assertThat(ok(call(s.admin(), HttpMethod.GET, base + "/activity?actorId=" + s.member().id(), null)).path("data")
                .path("items").findValuesAsText("verb")).containsOnly("task.created", "task.updated", "org.member.joined");
        assertThat(ok(call(s.admin(), HttpMethod.GET, base + "/activity?recordType=task", null)).path("data")
                .path("items").findValuesAsText("recordType")).containsOnly("task");
        for (JsonNode r : ok(call(s.admin(), HttpMethod.GET, base + "/activity?verb=org.", null)).path("data").path("items")) {
            assertThat(r.path("module").asText()).isEqualTo("org");
        }
        assertThat(ok(call(s.admin(), HttpMethod.GET, base + "/activity?to=2020-01-01", null)).path("data").path("items"))
                .isEmpty();
        assertThat(code(call(s.admin(), HttpMethod.GET, base + "/activity?from=2026-02-01&to=2026-01-01", null)))
                .isEqualTo("VALIDATION_FAILED");
        JsonNode page = ok(call(s.admin(), HttpMethod.GET, base + "/activity?size=2&page=1", null)).path("data");
        assertThat(page.path("items")).hasSize(2);
        assertThat(page.path("total").asLong()).isEqualTo(all.path("total").asLong());

        // Another organisation's admin sees none of it, and theirs is not here.
        User other = signUp("Other Admin");
        String otherOrg = newOrg(other);
        assertThat(code(call(other, HttpMethod.GET, base + "/activity", null))).isEqualTo("NOT_FOUND");
        ok(call(other, HttpMethod.POST, "/api/orgs/" + otherOrg + "/projects", Map.of("name", "Elsewhere", "key", "ELS")));
        assertThat(ok(call(other, HttpMethod.GET, "/api/orgs/" + otherOrg + "/activity", null)).path("data").path("items")
                .findValuesAsText("verb")).containsOnly("project.created");
        for (JsonNode r : ok(call(s.admin(), HttpMethod.GET, base + "/activity?size=100", null)).path("data").path("items")) {
            assertThat(r.path("data").path("key").asText()).isNotEqualTo("ELS");
        }
    }
}
