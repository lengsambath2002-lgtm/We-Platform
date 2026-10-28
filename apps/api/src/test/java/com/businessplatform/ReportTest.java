package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Reports count exactly what the caller may see, on a small fixed dataset whose history is set in SQL. */
class ReportTest extends IntegrationTest {

    @Autowired
    JdbcTemplate sql;

    private final LocalDate today = LocalDate.now(CalendarService.ZONE);

    private record Setup(User owner, User member, User outsider, User guest, String org) {
    }

    /**
     * ALP (org-visible): a1 done, a2 todo overdue (member), a3 in progress (owner) with a subtask, a4 todo
     * overdue unassigned created before the range. PRV (private: owner + member): p1 in review overdue (member),
     * p2 done. Approvals: a2 approved after 10 h, a3 rejected after 30 h, p1 pending for 12 days.
     */
    private Setup setup() throws Exception {
        User owner = signUp("Report Owner");
        User member = signUp("Report Member");
        User outsider = signUp("Report Outsider");
        User guest = signUp("Report Guest");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        join(owner, org, outsider, "MEMBER");
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Alpha", "key", "ALP",
                "members", List.of(Map.of("userId", member.id(), "role", "MEMBER")))));
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/projects", Map.of("name", "Private", "key", "PRV",
                "visibility", "PRIVATE", "members", List.of(Map.of("userId", member.id(), "role", "MEMBER")))));

        String a1 = task(owner, org, "ALP", Map.of("title", "a1", "status", "DONE"));
        String a2 = task(owner, org, "ALP", Map.of("title", "a2", "assigneeId", member.id(), "dueDate", today.minusDays(1)));
        String a3 = task(owner, org, "ALP", Map.of("title", "a3", "status", "IN_PROGRESS", "assigneeId", owner.id(),
                "dueDate", today.plusDays(5)));
        task(owner, org, "ALP", Map.of("title", "a3 sub", "parentId", a3, "assigneeId", member.id(),
                "dueDate", today.minusDays(3)));
        String a4 = task(owner, org, "ALP", Map.of("title", "a4", "dueDate", today.minusDays(2)));
        String p1 = task(owner, org, "PRV", Map.of("title", "p1", "status", "IN_REVIEW", "assigneeId", member.id(),
                "dueDate", today.minusDays(1)));
        String p2 = task(owner, org, "PRV", Map.of("title", "p2", "status", "DONE"));
        history(a1, 20, 10);
        history(a2, 20, null);
        history(a3, 3, null);
        history(a4, 40, null);
        history(p1, 5, null);
        history(p2, 5, 2);

        String flow = workflow(owner, org, "Project lead sign-off");
        String r1 = approval(member, org, flow, a2);
        String r2 = approval(member, org, flow, a3);
        approval(member, org, flow, p1);
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/approvals/" + r1 + "/approve", Map.of()));
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/approvals/" + r2 + "/reject", Map.of("comment", "No")));
        decided(r1, 10, 10);
        decided(r2, 8, 30);
        sql.update("UPDATE approval_request SET created_at = ? WHERE record_id = ?", at(12), UUID.fromString(p1));
        return new Setup(owner, member, outsider, guest, org);
    }

    private String task(User u, String org, String project, Map<String, Object> body) throws Exception {
        Map<String, Object> b = new HashMap<>(body);
        b.replaceAll((k, v) -> v instanceof LocalDate || v instanceof UUID ? v.toString() : v);
        return ok(call(u, HttpMethod.POST, "/api/orgs/" + org + "/projects/" + project + "/tasks", b))
                .path("data").path("id").asText();
    }

    private Timestamp at(int daysAgo) {
        return Timestamp.from(today.minusDays(daysAgo).atTime(LocalTime.NOON).atZone(CalendarService.ZONE).toInstant());
    }

    private void history(String taskId, int createdDaysAgo, Integer completedDaysAgo) {
        sql.update("UPDATE task SET created_at = ?, completed_at = ? WHERE id = ?", at(createdDaysAgo),
                completedDaysAgo == null ? null : at(completedDaysAgo), UUID.fromString(taskId));
    }

    private void decided(String requestId, int daysAgo, int afterHours) {
        Timestamp created = at(daysAgo);
        sql.update("UPDATE approval_request SET created_at = ?, decided_at = ? WHERE id = ?", created,
                new Timestamp(created.getTime() + afterHours * 3_600_000L), UUID.fromString(requestId));
    }

    private String workflow(User u, String org, String name) throws Exception {
        for (JsonNode w : ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/workflows", null)).path("data")) {
            if (w.path("name").asText().equals(name)) {
                return w.path("id").asText();
            }
        }
        throw new AssertionError(name);
    }

    private String approval(User u, String org, String workflowId, String taskId) throws Exception {
        return ok(call(u, HttpMethod.POST, "/api/orgs/" + org + "/approvals", Map.of("workflowId", workflowId,
                "moduleKey", "projects", "recordType", "task", "recordId", taskId))).path("data").path("summary")
                .path("id").asText();
    }

    private JsonNode report(User u, String org, String path) throws Exception {
        return call(u, HttpMethod.GET, "/api/orgs/" + org + "/reports/" + path, null);
    }

    private static JsonNode find(JsonNode list, String field, String value) {
        for (JsonNode n : list) {
            if (n.path(field).asText().equals(value)) {
                return n;
            }
        }
        throw new AssertionError(value + " not in " + list);
    }

    @Test
    void task_flow_counts_created_and_completed_per_week_in_visible_projects() throws Exception {
        Setup s = setup();
        String range = "task-flow?from=" + today.minusDays(29) + "&to=" + today;
        JsonNode all = ok(report(s.owner(), s.org(), range)).path("data");
        assertThat(all.path("created").asLong()).isEqualTo(5); // a1 a2 a3 p1 p2; a4 is older; subtasks don't count
        assertThat(all.path("completed").asLong()).isEqualTo(2);
        JsonNode weeks = all.path("weeks");
        assertThat(weeks.get(0).path("weekStart").asText())
                .isEqualTo(today.minusDays(29).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
        assertThat(weeks.get(weeks.size() - 1).path("weekStart").asText())
                .isEqualTo(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
        String week20 = today.minusDays(20).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        assertThat(find(weeks, "weekStart", week20).path("created").asLong()).isEqualTo(2);
        long sum = 0;
        for (JsonNode w : weeks) {
            sum += w.path("created").asLong();
        }
        assertThat(sum).isEqualTo(5);

        // The private project is invisible to a member outside it, and can't be picked.
        JsonNode outsider = ok(report(s.outsider(), s.org(), range)).path("data");
        assertThat(outsider.path("created").asLong()).isEqualTo(3);
        assertThat(outsider.path("completed").asLong()).isEqualTo(1);
        assertThat(code(report(s.outsider(), s.org(), range + "&project=PRV"))).isEqualTo("NOT_FOUND");
        JsonNode prv = ok(report(s.member(), s.org(), range + "&project=PRV")).path("data");
        assertThat(prv.path("created").asLong()).isEqualTo(2);
        assertThat(prv.path("completed").asLong()).isEqualTo(1);
    }

    @Test
    void open_work_and_workload_count_open_tasks_and_overdue() throws Exception {
        Setup s = setup();
        JsonNode open = ok(report(s.owner(), s.org(), "open-work")).path("data");
        assertThat(open.path("open").asLong()).isEqualTo(4);
        assertThat(open.path("overdue").asLong()).isEqualTo(3);
        JsonNode alp = find(open.path("projects"), "key", "ALP");
        assertThat(List.of(alp.path("todo").asLong(), alp.path("inProgress").asLong(), alp.path("inReview").asLong(),
                alp.path("overdue").asLong())).containsExactly(2L, 1L, 0L, 2L);
        JsonNode prv = find(open.path("projects"), "key", "PRV");
        assertThat(List.of(prv.path("inReview").asLong(), prv.path("overdue").asLong())).containsExactly(1L, 1L);

        JsonNode outsiderOpen = ok(report(s.outsider(), s.org(), "open-work")).path("data");
        assertThat(outsiderOpen.path("projects").findValuesAsText("key")).containsExactly("ALP");
        assertThat(outsiderOpen.path("open").asLong()).isEqualTo(3);

        JsonNode load = ok(report(s.owner(), s.org(), "workload")).path("data");
        assertThat(load.path("open").asLong()).isEqualTo(4);
        JsonNode member = find(load.path("people"), "name", "Report Member");
        assertThat(List.of(member.path("open").asLong(), member.path("overdue").asLong())).containsExactly(2L, 2L);
        JsonNode owner = find(load.path("people"), "name", "Report Owner");
        assertThat(List.of(owner.path("open").asLong(), owner.path("overdue").asLong())).containsExactly(1L, 0L);
        JsonNode unassigned = find(load.path("people"), "id", "null");
        assertThat(unassigned.path("name").isNull()).isTrue();
        assertThat(List.of(unassigned.path("open").asLong(), unassigned.path("overdue").asLong())).containsExactly(1L, 1L);

        JsonNode outsiderLoad = ok(report(s.outsider(), s.org(), "workload?project=ALP")).path("data");
        assertThat(find(outsiderLoad.path("people"), "name", "Report Member").path("open").asLong()).isEqualTo(1);
    }

    @Test
    void approvals_report_outcomes_decision_time_and_stale_requests() throws Exception {
        Setup s = setup();
        String range = "approvals?from=" + today.minusDays(29) + "&to=" + today;
        JsonNode all = ok(report(s.owner(), s.org(), range)).path("data");
        JsonNode total = all.path("total");
        assertThat(List.of(total.path("submitted").asLong(), total.path("approved").asLong(),
                total.path("rejected").asLong(), total.path("pending").asLong(), total.path("stale").asLong()))
                .containsExactly(3L, 1L, 1L, 1L, 1L);
        assertThat(total.path("medianHours").asDouble()).isEqualTo(20.0);
        assertThat(total.path("p90Hours").asDouble()).isEqualTo(28.0);
        assertThat(all.path("staleDays").asInt()).isEqualTo(7);
        JsonNode flow = find(all.path("workflows"), "name", "Project lead sign-off").path("stats");
        assertThat(flow.path("submitted").asLong()).isEqualTo(3);

        // Waiting 12 days is not stale at a 14-day threshold.
        assertThat(ok(report(s.owner(), s.org(), range + "&staleDays=14")).path("data").path("total").path("stale")
                .asLong()).isZero();
        // A member outside the private project sees only requests about tasks they can see.
        JsonNode outsider = ok(report(s.outsider(), s.org(), range)).path("data").path("total");
        assertThat(List.of(outsider.path("submitted").asLong(), outsider.path("pending").asLong(),
                outsider.path("stale").asLong())).containsExactly(2L, 0L, 0L);
        // Nothing decided in an older range: counts are zero and percentiles empty.
        JsonNode old = ok(report(s.owner(), s.org(), "approvals?from=" + today.minusDays(200) + "&to="
                + today.minusDays(100))).path("data").path("total");
        assertThat(old.path("submitted").asLong()).isZero();
        assertThat(old.path("medianHours").isNull()).isTrue();
    }

    @Test
    void guests_outsiders_and_bad_ranges_are_refused() throws Exception {
        Setup s = setup();
        assertThat(code(report(s.guest(), s.org(), "open-work"))).isEqualTo("FORBIDDEN");
        assertThat(code(report(s.guest(), s.org(), "approvals"))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.member(), HttpMethod.GET, "/api/orgs/" + s.org() + "/context", null)).toString())
                .contains("reports.view");
        assertThat(ok(call(s.guest(), HttpMethod.GET, "/api/orgs/" + s.org() + "/context", null)).toString())
                .doesNotContain("reports.view");

        // Another organisation's owner learns nothing; their own reports don't include this organisation's work.
        User stranger = signUp("Stranger");
        String other = newOrg(stranger);
        assertThat(code(report(stranger, s.org(), "open-work"))).isEqualTo("NOT_FOUND");
        assertThat(code(report(stranger, s.org(), "approvals"))).isEqualTo("NOT_FOUND");
        assertThat(ok(report(stranger, other, "open-work")).path("data").path("open").asLong()).isZero();
        assertThat(ok(report(stranger, other, "approvals")).path("data").path("total").path("submitted").asLong())
                .isZero();
        assertThat(code(report(stranger, other, "workload?project=ALP"))).isEqualTo("NOT_FOUND");

        JsonNode backwards = callIn("km", s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/reports/task-flow?from="
                + today + "&to=" + today.minusDays(1), null);
        assertThat(code(backwards)).isEqualTo("VALIDATION_FAILED");
        assertThat(backwards.path("error").path("fields").get(0).path("message").asText())
                .isEqualTo("ថ្ងៃចាប់ផ្ដើមត្រូវតែនៅមុន ឬត្រូវនឹងថ្ងៃបញ្ចប់");
        assertThat(code(report(s.owner(), s.org(), "task-flow?from=" + today.minusDays(366) + "&to=" + today)))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(report(s.owner(), s.org(), "task-flow?from=" + today.minusDays(365) + "&to=" + today)))
                .isEqualTo("SUCCESS");
        assertThat(code(report(s.owner(), s.org(), "approvals?from=yesterday"))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(report(s.owner(), s.org(), "approvals?staleDays=0"))).isEqualTo("VALIDATION_FAILED");
    }
}
