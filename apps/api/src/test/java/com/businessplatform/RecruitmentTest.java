package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Recruitment: openings, the pipeline, interviews and blind feedback, hiring with an invitation, onboarding. */
class RecruitmentTest extends IntegrationTest {

    private record Setup(User owner, User manager, User interviewer, User outsider, String org) {

        String rec() {
            return "/api/orgs/" + org + "/hr/recruitment";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Rec Owner");
        User manager = signUp("Rec Manager");
        User interviewer = signUp("Rec Interviewer");
        User outsider = signUp("Rec Outsider");
        String org = newOrg(owner);
        join(owner, org, manager, "MEMBER");
        join(owner, org, interviewer, "MEMBER");
        join(owner, org, outsider, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        return new Setup(owner, manager, interviewer, outsider, org);
    }

    private String job(Setup s, int openings) throws Exception {
        Map<String, Object> job = new HashMap<>(Map.of("title", "Sales officer", "department", "Sales", "employmentType", "FULL_TIME",
                "openings", openings, "hiringManagerId", s.manager().id().toString()));
        return ok(call(s.owner(), HttpMethod.POST, s.rec() + "/jobs", job)).path("data").path("id").asText();
    }

    private String perms(User u, Setup s) throws Exception {
        return ok(call(u, HttpMethod.GET, "/api/orgs/" + s.org() + "/context", null)).path("data").path("permissions").toString();
    }

    @Test
    void only_hr_hiring_managers_and_interviewers_take_part() throws Exception {
        Setup s = setup();
        assertThat(perms(s.manager(), s)).doesNotContain("hr.recruit");
        assertThat(code(call(s.manager(), HttpMethod.POST, s.rec() + "/jobs", Map.of("title", "X", "openings", 1))))
                .isEqualTo("FORBIDDEN");
        String job = job(s, 1);
        assertThat(perms(s.manager(), s)).contains("hr.recruit");
        assertThat(perms(s.outsider(), s)).doesNotContain("hr.recruit");
        assertThat(ok(call(s.manager(), HttpMethod.GET, s.rec() + "/jobs", null)).path("data")).hasSize(1);
        assertThat(ok(call(s.outsider(), HttpMethod.GET, s.rec() + "/jobs", null)).path("data")).isEmpty();
        assertThat(code(call(s.outsider(), HttpMethod.GET, s.rec() + "/jobs/" + job + "/candidates", null))).isEqualTo("NOT_FOUND");

        // The hiring manager adds and moves candidates but can't hire.
        JsonNode c = ok(call(s.manager(), HttpMethod.POST, s.rec() + "/jobs/" + job + "/candidates",
                Map.of("name", "Chenda Lim", "email", "chenda@example.com", "source", "REFERRAL"))).path("data");
        String id = c.path("candidate").path("id").asText();
        assertThat(c.path("canHire").asBoolean()).isFalse();
        ok(call(s.manager(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/stage", Map.of("stage", "SCREENING")));
        assertThat(code(call(s.manager(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/hire",
                Map.of("startDate", LocalDate.now().plusDays(14).toString(), "invite", false)))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.outsider(), HttpMethod.GET, s.rec() + "/candidates/" + id, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(s.manager(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/stage", Map.of("stage", "REJECTED"))))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void interviewers_see_each_others_feedback_only_after_giving_their_own() throws Exception {
        Setup s = setup();
        String job = job(s, 1);
        String id = ok(call(s.owner(), HttpMethod.POST, s.rec() + "/jobs/" + job + "/candidates",
                Map.of("name", "Vanna Ouk", "source", "JOB_BOARD"))).path("data").path("candidate").path("id").asText();
        JsonNode d = ok(call(s.manager(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/interviews", Map.of(
                "startsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "durationMinutes", 45, "location", "Meeting room",
                "panel", List.of(s.manager().id().toString(), s.interviewer().id().toString())))).path("data");
        assertThat(d.path("candidate").path("stage").asText()).isEqualTo("INTERVIEW");
        String interview = d.path("interviews").get(0).path("id").asText();
        assertThat(perms(s.interviewer(), s)).contains("hr.recruit");
        assertThat(ok(call(s.interviewer(), HttpMethod.GET, s.rec() + "/interviews/mine", null)).path("data")).hasSize(1);

        ok(call(s.manager(), HttpMethod.PUT, s.rec() + "/interviews/" + interview + "/feedback",
                Map.of("recommendation", "STRONG_YES", "notes", "Great with customers")));
        // The interviewer sees the candidate, but not the manager's view before giving their own.
        JsonNode before = ok(call(s.interviewer(), HttpMethod.GET, s.rec() + "/candidates/" + id, null)).path("data");
        assertThat(before.path("canManage").asBoolean()).isFalse();
        assertThat(before.toString()).doesNotContain("Great with customers").doesNotContain("STRONG_YES");
        assertThat(code(call(s.interviewer(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/stage", Map.of("stage", "OFFER"))))
                .isEqualTo("FORBIDDEN");
        JsonNode after = ok(call(s.interviewer(), HttpMethod.PUT, s.rec() + "/interviews/" + interview + "/feedback",
                Map.of("recommendation", "YES", "notes", "Solid"))).path("data");
        assertThat(after.toString()).contains("Great with customers").contains("STRONG_YES");
        assertThat(code(call(s.outsider(), HttpMethod.PUT, s.rec() + "/interviews/" + interview + "/feedback",
                Map.of("recommendation", "NO")))).isIn("FORBIDDEN", "NOT_FOUND");
    }

    @Test
    void hiring_invites_the_candidate_copies_onboarding_and_fills_the_opening() throws Exception {
        Setup s = setup();
        String job = job(s, 1);
        String email = "new.hire." + System.nanoTime() + "@example.com";
        String id = ok(call(s.owner(), HttpMethod.POST, s.rec() + "/jobs/" + job + "/candidates",
                Map.of("name", "Sreymom Keo", "email", email, "source", "WEBSITE", "rating", 4))).path("data")
                .path("candidate").path("id").asText();
        LocalDate start = LocalDate.now().plusDays(10);
        JsonNode hired = ok(call(s.owner(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/hire",
                Map.of("startDate", start.toString(), "invite", true))).path("data");
        assertThat(hired.path("candidate").path("stage").asText()).isEqualTo("HIRED");
        assertThat(hired.path("invited").asBoolean()).isTrue();
        assertThat(hired.path("joined").asBoolean()).isFalse();
        assertThat(hired.path("job").path("status").asText()).isEqualTo("CLOSED");
        JsonNode steps = hired.path("onboarding");
        assertThat(steps).hasSize(6);
        assertThat(steps.get(0).path("dueDate").asText()).isEqualTo(start.minusDays(3).toString());
        assertThat(mails.await(email, 1).text()).contains("invite");

        // The hiring manager ticks a step off; HR sees progress.
        ok(call(s.manager(), HttpMethod.PUT, s.rec() + "/onboarding/" + steps.get(1).path("id").asText(), Map.of("done", true)));
        JsonNode hires = ok(call(s.owner(), HttpMethod.GET, s.rec() + "/hires", null)).path("data");
        assertThat(hires.get(0).path("done").asInt()).isEqualTo(1);
        assertThat(hires.get(0).path("total").asInt()).isEqualTo(6);
        // A closed opening takes no new candidates, and a hire can't be moved back.
        assertThat(code(call(s.owner(), HttpMethod.POST, s.rec() + "/jobs/" + job + "/candidates",
                Map.of("name", "Late", "source", "OTHER")))).isEqualTo("INVALID_STATE");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.rec() + "/candidates/" + id + "/stage", Map.of("stage", "NEW"))))
                .isEqualTo("INVALID_STATE");
        JsonNode log = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null)).path("data");
        assertThat(log.findValuesAsText("verb")).contains("candidate.hired", "job.filled");
    }

    @Test
    void hr_edits_the_onboarding_steps() throws Exception {
        Setup s = setup();
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.rec() + "/onboarding-template", null)).path("data").path("items")).hasSize(6);
        ok(call(s.owner(), HttpMethod.PUT, s.rec() + "/onboarding-template",
                Map.of("items", List.of(Map.of("title", "Laptop", "dueDays", -1), Map.of("title", "Buddy lunch", "dueDays", 2)))));
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.rec() + "/onboarding-template", null)).path("data").path("items")
                .findValuesAsText("title")).containsExactly("Laptop", "Buddy lunch");
        assertThat(code(call(s.manager(), HttpMethod.GET, s.rec() + "/onboarding-template", null))).isEqualTo("FORBIDDEN");
    }
}
