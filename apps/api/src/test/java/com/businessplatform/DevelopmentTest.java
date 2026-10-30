package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Performance reviews (self → reviewer → shared → acknowledged) and training (courses, enrolment, completion). */
class DevelopmentTest extends IntegrationTest {

    private record Setup(User owner, User manager, User employee, String org) {

        String hr() {
            return "/api/orgs/" + org + "/hr";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Dev Owner");
        User manager = signUp("Dev Manager");
        User employee = signUp("Dev Employee");
        String org = newOrg(owner);
        join(owner, org, manager, "MEMBER");
        join(owner, org, employee, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        Setup s = new Setup(owner, manager, employee, org);
        ok(call(owner, HttpMethod.PUT, s.hr() + "/people/" + employee.id(), new HashMap<>(Map.of("managerId", manager.id()))));
        return s;
    }

    private static Map<String, Object> answer(Integer rating, String comment) {
        Map<String, Object> m = new HashMap<>();
        m.put("rating", rating);
        m.put("comment", comment);
        return m;
    }

    private static Map<String, Object> answers(Integer a, Integer b, boolean submit, Integer overall) {
        Map<String, Object> m = new HashMap<>();
        m.put("answers", List.of(answer(a, "First"), answer(b, "Second")));
        m.put("summary", "A good year");
        m.put("overallRating", overall);
        m.put("submit", submit);
        return m;
    }

    @Test
    void a_review_goes_from_self_assessment_to_the_manager_and_back_to_the_person() throws Exception {
        Setup s = setup();
        Map<String, Object> cycle = Map.of("name", "2026 annual review", "periodStart", "2026-01-01", "periodEnd", "2026-12-31",
                "dueDate", LocalDate.now().plusDays(30).toString(), "questions", List.of("Quality of work", "Teamwork"));
        assertThat(code(call(s.manager(), HttpMethod.POST, s.hr() + "/review-cycles", cycle))).isEqualTo("FORBIDDEN");
        String id = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/review-cycles", cycle)).path("data").path("id").asText();
        JsonNode launched = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/review-cycles/" + id + "/launch", null)).path("data");
        assertThat(launched.path("cycle").path("total").asInt()).isEqualTo(3);
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.hr() + "/review-cycles/" + id, cycle))).isEqualTo("INVALID_STATE");

        JsonNode mine = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/reviews/mine", null)).path("data");
        String review = mine.get(0).path("id").asText();
        assertThat(mine.get(0).path("reviewer").path("id").asText()).isEqualTo(s.manager().id().toString());
        // The manager can't review before the self-assessment; the person can't send it half done.
        assertThat(code(call(s.manager(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/review", answers(4, 4, true, 4))))
                .isEqualTo("INVALID_STATE");
        assertThat(code(call(s.employee(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/self", answers(4, null, true, null))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(s.employee(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/self", answers(4, null, false, null)));
        JsonNode sent = ok(call(s.employee(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/self", answers(4, 5, true, null))).path("data");
        assertThat(sent.path("status").asText()).isEqualTo("MANAGER");

        // The manager sees the self-assessment, drafts, then shares with an overall rating.
        JsonNode toReview = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/reviews/to-review", null)).path("data");
        assertThat(toReview).hasSize(1);
        JsonNode seen = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/reviews/" + review, null)).path("data");
        assertThat(seen.path("selfAnswers").get(1).path("rating").asInt()).isEqualTo(5);
        ok(call(s.manager(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/review", answers(3, 4, false, null)));
        // While it is a draft, the person sees neither the manager's answers nor the rating.
        JsonNode hidden = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/reviews/" + review, null)).path("data");
        assertThat(hidden.path("managerAnswers").isNull()).isTrue();
        assertThat(code(call(s.manager(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/review", answers(3, 4, true, null))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(s.manager(), HttpMethod.PUT, s.hr() + "/reviews/" + review + "/review", answers(3, 4, true, 4)));

        JsonNode shared = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/reviews/" + review, null)).path("data");
        assertThat(shared.path("status").asText()).isEqualTo("DONE");
        assertThat(shared.path("overallRating").asInt()).isEqualTo(4);
        assertThat(shared.path("canAcknowledge").asBoolean()).isTrue();
        ok(call(s.employee(), HttpMethod.POST, s.hr() + "/reviews/" + review + "/acknowledge", null));

        // HR sees the cycle's progress and closes it; nothing changes after, and others' reviews stay private.
        JsonNode cycleView = ok(call(s.owner(), HttpMethod.GET, s.hr() + "/review-cycles/" + id, null)).path("data").path("cycle");
        assertThat(cycleView.path("acknowledged").asInt()).isEqualTo(1);
        ok(call(s.owner(), HttpMethod.POST, s.hr() + "/review-cycles/" + id + "/close", null));
        String managers = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/reviews/mine", null)).path("data").get(0).path("id").asText();
        assertThat(code(call(s.manager(), HttpMethod.PUT, s.hr() + "/reviews/" + managers + "/self", answers(4, 4, false, null))))
                .isEqualTo("INVALID_STATE");
        assertThat(code(call(s.employee(), HttpMethod.GET, s.hr() + "/reviews/" + managers, null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void people_sign_up_for_courses_within_capacity_and_hr_marks_completion() throws Exception {
        Setup s = setup();
        Map<String, Object> course = new HashMap<>(Map.of("title", "Customer service basics", "provider", "Training Co.",
                "hours", 6, "capacity", 1, "selfEnrol", true, "startsOn", LocalDate.now().plusDays(7).toString()));
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/training/courses", course))).isEqualTo("FORBIDDEN");
        String id = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/training/courses", course)).path("data").path("course").path("id").asText();

        JsonNode listed = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/training/courses", null)).path("data");
        assertThat(listed.get(0).path("canEnrol").asBoolean()).isTrue();
        ok(call(s.employee(), HttpMethod.POST, s.hr() + "/training/courses/" + id + "/enrol", null));
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/training/courses/" + id + "/enrol", null))).isEqualTo("CONFLICT");
        assertThat(code(call(s.manager(), HttpMethod.POST, s.hr() + "/training/courses/" + id + "/enrol", null))).isEqualTo("INVALID_STATE");

        // HR sees the enrolment and marks it completed; it shows on the person's record.
        JsonNode detail = ok(call(s.owner(), HttpMethod.GET, s.hr() + "/training/courses/" + id, null)).path("data");
        String enrolment = detail.path("enrolments").get(0).path("id").asText();
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.hr() + "/training/courses/" + id, null)).path("data").path("enrolments")).isEmpty();
        assertThat(code(call(s.employee(), HttpMethod.PUT, s.hr() + "/training/enrolments/" + enrolment, Map.of("status", "COMPLETED"))))
                .isEqualTo("FORBIDDEN");
        ok(call(s.owner(), HttpMethod.PUT, s.hr() + "/training/enrolments/" + enrolment, Map.of("status", "COMPLETED")));
        JsonNode record = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/training/mine", null)).path("data");
        assertThat(record.get(0).path("status").asText()).isEqualTo("COMPLETED");

        // HR enrols someone else once there is room again.
        course.put("capacity", 2);
        ok(call(s.owner(), HttpMethod.PUT, s.hr() + "/training/courses/" + id, course));
        JsonNode after = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/training/courses/" + id + "/enrolments",
                Map.of("userIds", List.of(s.manager().id().toString())))).path("data");
        assertThat(after.path("course").path("enrolled").asInt()).isEqualTo(2);
    }
}
