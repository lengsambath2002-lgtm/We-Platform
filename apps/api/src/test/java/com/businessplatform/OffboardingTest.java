package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Offboarding: a resignation, HR accepting it, the handover warnings, the checklist, and access ending. */
class OffboardingTest extends IntegrationTest {

    private static LocalDate today() {
        return LocalDate.now(CalendarService.ZONE);
    }

    @Test
    void a_resignation_is_accepted_handed_over_and_completed() throws Exception {
        User owner = signUp("Off Owner");
        User lead = signUp("Off Lead");
        User member = signUp("Off Member");
        String org = newOrg(owner);
        join(owner, org, lead, "MEMBER");
        join(owner, org, member, "MEMBER");
        String api = "/api/orgs/" + org;
        ok(call(owner, HttpMethod.PUT, api + "/modules/hr", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, api + "/hr/people/" + member.id(), new HashMap<>(Map.of("managerId", lead.id()))));
        Map<String, Object> unit = new HashMap<>(Map.of("name", "Sales", "kind", "DEPARTMENT", "headUserId", lead.id()));
        ok(call(owner, HttpMethod.POST, api + "/structure/units", unit));
        String type = ok(call(owner, HttpMethod.GET, api + "/hr/leave-types", null)).path("data").get(0).path("id").asText();
        ok(call(member, HttpMethod.POST, api + "/hr/leave", Map.of("leaveTypeId", type, "startDate", "2026-12-14", "endDate", "2026-12-14",
                "halfStart", false, "halfEnd", false)));
        String off = api + "/hr/offboarding";

        // The lead resigns; HR (the owner) is told; colleagues don't see it.
        assertThat(code(call(lead, HttpMethod.POST, off + "/resign", Map.of("lastDay", today().minusDays(1).toString())))).isEqualTo("VALIDATION_FAILED");
        JsonNode r = ok(call(lead, HttpMethod.POST, off + "/resign", Map.of("lastDay", today().plusDays(14).toString(), "reason", "Moving abroad"))).path("data");
        String id = r.path("summary").path("id").asText();
        assertThat(r.path("summary").path("status").asText()).isEqualTo("REQUESTED");
        assertThat(code(call(lead, HttpMethod.POST, off + "/resign", Map.of("lastDay", today().plusDays(20).toString())))).isEqualTo("CONFLICT");
        assertThat(ok(call(owner, HttpMethod.GET, api + "/notifications", null)).path("data").path("items").findValuesAsText("type"))
                .contains("offboarding.resigned");
        assertThat(code(call(member, HttpMethod.GET, off + "/" + id, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(lead, HttpMethod.POST, off + "/" + id + "/accept", Map.of("kind", "RESIGNATION", "lastDay", today().toString()))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(ok(call(lead, HttpMethod.GET, off + "/mine", null)).path("data").path("summary").path("id").asText()).isEqualTo(id);

        // HR accepts it with today as the last day: the checklist and what to hand over.
        JsonNode d = ok(call(owner, HttpMethod.POST, off + "/" + id + "/accept", Map.of("kind", "RESIGNATION", "lastDay", today().toString(),
                "rehire", true))).path("data");
        assertThat(d.path("summary").path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(d.path("items")).hasSize(8);
        assertThat(d.path("handover").path("reports").findValuesAsText("name")).containsExactly("Off Member");
        assertThat(d.path("handover").path("waitingApprovals").asLong()).isEqualTo(1);
        assertThat(d.path("handover").path("unitsHeaded").get(0).asText()).isEqualTo("Sales");
        assertThat(d.path("leave")).isNotEmpty();
        assertThat(ok(call(owner, HttpMethod.GET, off + "?open=true", null)).path("data").findValuesAsText("status")).contains("IN_PROGRESS");

        // Completing needs every step done; then the lead loses access.
        assertThat(code(call(owner, HttpMethod.POST, off + "/" + id + "/complete", null))).isEqualTo("INVALID_STATE");
        for (JsonNode i : d.path("items")) {
            ok(call(owner, HttpMethod.PUT, off + "/items/" + i.path("id").asText() + "/done", Map.of("done", true)));
        }
        JsonNode done = ok(call(owner, HttpMethod.POST, off + "/" + id + "/complete", null)).path("data");
        assertThat(done.path("summary").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(code(call(lead, HttpMethod.GET, api + "/context", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(owner, HttpMethod.GET, off + "?open=false", null)).path("data").findValuesAsText("status")).contains("COMPLETED");
    }

    @Test
    void hr_starts_a_departure_with_its_own_checklist_and_waits_for_the_last_day() throws Exception {
        User owner = signUp("Off Owner2");
        User member = signUp("Off Member2");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String api = "/api/orgs/" + org;
        ok(call(owner, HttpMethod.PUT, api + "/modules/hr", Map.of("enabled", true)));
        String off = api + "/hr/offboarding";
        assertThat(ok(call(owner, HttpMethod.GET, off + "/template", null)).path("data")).hasSize(8);
        ok(call(owner, HttpMethod.PUT, off + "/template", Map.of("items", List.of(Map.of("title", "Return laptop", "dueDays", 0),
                Map.of("title", "Handover", "dueDays", -2)))));
        assertThat(code(call(member, HttpMethod.POST, off, Map.of("userId", owner.id(), "kind", "TERMINATION",
                "lastDay", today().plusDays(30).toString())))).isEqualTo("FORBIDDEN");
        assertThat(code(call(owner, HttpMethod.POST, off, Map.of("userId", owner.id(), "kind", "OTHER",
                "lastDay", today().plusDays(30).toString())))).isEqualTo("VALIDATION_FAILED");
        JsonNode d = ok(call(owner, HttpMethod.POST, off, Map.of("userId", member.id(), "kind", "END_OF_CONTRACT",
                "lastDay", today().plusDays(30).toString()))).path("data");
        assertThat(d.path("items").findValuesAsText("title")).containsExactly("Return laptop", "Handover");
        assertThat(d.path("items").get(1).path("dueDate").asText()).isEqualTo(today().plusDays(28).toString());
        String id = d.path("summary").path("id").asText();
        for (JsonNode i : d.path("items")) {
            ok(call(owner, HttpMethod.PUT, off + "/items/" + i.path("id").asText() + "/done", Map.of("done", true)));
        }
        assertThat(code(call(owner, HttpMethod.POST, off + "/" + id + "/complete", null))).isEqualTo("INVALID_STATE");
        // The person sees their departure; HR cancels it.
        assertThat(ok(call(member, HttpMethod.GET, off + "/" + id, null)).path("data").path("canManage").asBoolean()).isFalse();
        assertThat(code(call(member, HttpMethod.POST, off + "/" + id + "/cancel", null))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(owner, HttpMethod.POST, off + "/" + id + "/cancel", null)).path("data").path("summary").path("status").asText())
                .isEqualTo("CANCELLED");
    }
}
