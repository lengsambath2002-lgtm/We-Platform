package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Bulk import of people and the structure: checked first, applied all at once, safe to repeat. */
class PeopleImportTest extends IntegrationTest {

    private static Map<String, Object> row(String name, String email, String path, String kinds, String position, String manager,
            String second, boolean head) {
        Map<String, Object> r = new HashMap<>();
        r.put("name", name);
        r.put("email", email);
        r.put("unitPath", path);
        r.put("unitKinds", kinds);
        r.put("position", position);
        r.put("managerEmail", manager);
        r.put("secondManagerEmail", second);
        r.put("unitHead", head);
        return r;
    }

    @Test
    void a_company_is_imported_with_its_structure_and_approval_line() throws Exception {
        User owner = signUp("Imp Owner");
        User elsewhere = signUp("Imp Elsewhere");
        User member = signUp("Imp Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String id = UUID.randomUUID().toString().substring(0, 8);
        String lead = "lead-" + id + "@import.test";
        String dev = "dev-" + id + "@import.test";
        Map<String, Object> devRow = row("Ly Dev", dev, "Mobile Center > Mobile", "CENTER > TEAM", "Engineer I", lead, owner.email(), false);
        devRow.put("employmentType", "full time");
        devRow.put("startDate", "2024-01-15");
        List<Map<String, Object>> rows = List.of(
                row("Bo Lead", lead, "Mobile Center > Mobile", "CENTER > TEAM", "Team Lead", owner.email(), null, true),
                devRow,
                row("Somebody", elsewhere.email(), "Mobile Center > Mobile", null, null, null, null, false),
                row(null, "not-an-email", null, null, null, null, null, false));
        String base = "/api/orgs/" + org + "/people-import";
        assertThat(code(call(member, HttpMethod.POST, base + "/preview", Map.of("rows", rows)))).isEqualTo("FORBIDDEN");

        // A bad row stops the whole import; the preview says which and why.
        JsonNode p = ok(call(owner, HttpMethod.POST, base + "/preview", Map.of("rows", rows))).path("data");
        assertThat(p.path("errors").asInt()).isEqualTo(1);
        assertThat(p.path("rows").get(3).path("action").asText()).isEqualTo("ERROR");
        assertThat(p.path("rows").get(0).path("action").asText()).isEqualTo("CREATE");
        assertThat(p.path("rows").get(2).path("action").asText()).isEqualTo("INVITE");
        assertThat(p.path("newUnits").findValuesAsText("")).isEmpty();
        assertThat(p.path("newUnits")).hasSize(2);
        assertThat(p.path("newPositions")).hasSize(2);
        JsonNode refused = ok(call(owner, HttpMethod.POST, base, Map.of("rows", rows))).path("data");
        assertThat(refused.path("applied").asBoolean()).isFalse();
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/structure", null)).path("data").path("units")).isEmpty();

        // Without it, everything is applied at once.
        List<Map<String, Object>> good = rows.subList(0, 3);
        JsonNode r = ok(call(owner, HttpMethod.POST, base, Map.of("rows", good))).path("data");
        assertThat(r.path("applied").asBoolean()).isTrue();
        assertThat(r.path("creates").asInt()).isEqualTo(2);
        assertThat(r.path("invites").asInt()).isEqualTo(1);

        JsonNode s = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/structure", null)).path("data");
        List<String> names = new java.util.ArrayList<>();
        s.path("units").forEach(u -> names.add(u.path("name").asText()));
        assertThat(names).containsExactlyInAnyOrder("Mobile Center", "Mobile");
        for (JsonNode u : s.path("units")) {
            assertThat(u.path("kind").asText()).isEqualTo(u.path("name").asText().equals("Mobile") ? "TEAM" : "CENTER");
            if (u.path("name").asText().equals("Mobile")) {
                assertThat(u.path("head").path("name").asText()).isEqualTo("Bo Lead");
            }
        }
        assertThat(s.path("assignments")).hasSize(2);

        // The new people got an email to choose a password; then they sign in.
        MailMessage m = mails.await(dev, 1);
        assertThat(m.subject()).contains("added you to");
        Matcher t = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(m.html());
        assertThat(t.find()).isTrue();
        JsonNode session = ok(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", t.group(1), "password", "Welcome2026x"), "mobile")).path("data");
        assertThat(session.path("accessToken").asText()).isNotBlank();
        String devId = session.path("user").path("id").asText();

        // HR: manager, 2nd approver, employment and the approval line.
        JsonNode prof = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/hr/people/" + devId, null)).path("data");
        assertThat(prof.path("manager").path("name").asText()).isEqualTo("Bo Lead");
        assertThat(prof.path("title").asText()).isEqualTo("Engineer I");
        assertThat(prof.path("department").asText()).isEqualTo("Mobile");
        assertThat(prof.path("employmentType").asText()).isEqualTo("FULL_TIME");
        assertThat(prof.path("startDate").asText()).isEqualTo("2024-01-15");
        assertThat(prof.path("approvalLine").findValuesAsText("name")).containsExactly("Bo Lead", "Imp Owner");

        // Importing the same file again changes nothing more.
        JsonNode again = ok(call(owner, HttpMethod.POST, base + "/preview", Map.of("rows", good))).path("data");
        assertThat(again.path("rows").get(0).path("action").asText()).isEqualTo("UPDATE");
        assertThat(again.path("newUnits")).isEmpty();
        assertThat(again.path("newPositions")).isEmpty();
        ok(call(owner, HttpMethod.POST, base, Map.of("rows", good)));
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/structure", null)).path("data").path("units")).hasSize(2);
    }

    @Test
    void a_unit_name_may_contain_a_slash() throws Exception {
        User owner = signUp("Imp Owner3");
        String org = newOrg(owner);
        String a = "s-" + UUID.randomUUID().toString().substring(0, 8) + "@import.test";
        JsonNode p = ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/people-import/preview", Map.of("rows", List.of(
                row("A", a, "Dispatch Center > Other Outsourcing / Dispatch", "CENTER > TEAM", null, null, null, false))))).path("data");
        assertThat(p.path("newUnits").get(1).asText()).isEqualTo("Dispatch Center › Other Outsourcing / Dispatch");
        assertThat(p.path("newUnits")).hasSize(2);
    }

    @Test
    void managers_that_go_round_in_a_circle_are_refused() throws Exception {
        User owner = signUp("Imp Owner2");
        String org = newOrg(owner);
        String a = "a-" + UUID.randomUUID().toString().substring(0, 8) + "@import.test";
        String b = "b-" + UUID.randomUUID().toString().substring(0, 8) + "@import.test";
        JsonNode p = ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/people-import/preview", Map.of("rows", List.of(
                row("A", a, null, null, null, b, null, false), row("B", b, null, null, null, a, null, false))))).path("data");
        assertThat(p.path("errors").asInt()).isEqualTo(2);
    }
}
