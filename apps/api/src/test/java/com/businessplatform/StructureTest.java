package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The organisation structure: units, positions, placements, and what HR takes from them. */
class StructureTest extends IntegrationTest {

    private static Map<String, Object> unit(String name, String kind, Object parentId) {
        Map<String, Object> m = new HashMap<>(Map.of("name", name, "kind", kind));
        m.put("parentId", parentId);
        return m;
    }

    private static Map<String, Object> position(String title, Object unitId, Object reportsToId, int headcount) {
        Map<String, Object> m = new HashMap<>(Map.of("title", title, "headcount", headcount));
        m.put("unitId", unitId);
        m.put("reportsToId", reportsToId);
        return m;
    }

    private static Map<String, Object> place(Object unitId, Object positionId) {
        Map<String, Object> p = new HashMap<>();
        p.put("unitId", unitId);
        p.put("positionId", positionId);
        return Map.of("primary", p, "additional", List.of());
    }

    private String membershipOf(User admin, String org, User who) throws Exception {
        for (JsonNode m : ok(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(who.id().toString())) {
                return m.path("membershipId").asText();
            }
        }
        throw new AssertionError("not a member");
    }

    private static JsonNode find(JsonNode list, String id) {
        for (JsonNode n : list) {
            if (n.path("id").asText().equals(id)) {
                return n;
            }
        }
        throw new AssertionError(id + " not found");
    }

    @Test
    void units_positions_and_placements_build_the_structure_and_feed_hr() throws Exception {
        User owner = signUp("Str Owner");
        User officer = signUp("Str Officer");
        User guest = signUp("Str Guest");
        String org = newOrg(owner);
        join(owner, org, officer, "MEMBER");
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String s = "/api/orgs/" + org + "/structure";

        // Phnom Penh branch → Sales department (headed by the owner) → Sales Team A.
        String branch = ok(call(owner, HttpMethod.POST, s + "/units", unit("Phnom Penh", "BRANCH", null))).path("data").path("id").asText();
        Map<String, Object> salesIn = unit("Sales", "DEPARTMENT", branch);
        salesIn.put("code", "SAL");
        String sales = ok(call(owner, HttpMethod.POST, s + "/units", salesIn)).path("data").path("id").asText();
        String team = ok(call(owner, HttpMethod.POST, s + "/units", unit("Sales Team A", "TEAM", sales))).path("data").path("id").asText();

        // Names are unique among siblings, codes across the organisation; a unit can't go under its own sub-unit.
        assertThat(code(call(owner, HttpMethod.POST, s + "/units", unit("sales", "DEPARTMENT", branch)))).isEqualTo("CONFLICT");
        Map<String, Object> dupCode = unit("Marketing", "DEPARTMENT", branch);
        dupCode.put("code", "sal");
        assertThat(code(call(owner, HttpMethod.POST, s + "/units", dupCode))).isEqualTo("CONFLICT");
        assertThat(code(call(owner, HttpMethod.PUT, s + "/units/" + sales, unit("Sales", "DEPARTMENT", team)))).isEqualTo("VALIDATION_FAILED");
        // Only Owners and Admins change it; guests don't see it.
        assertThat(code(call(officer, HttpMethod.POST, s + "/units", unit("Mine", "TEAM", null)))).isEqualTo("FORBIDDEN");
        assertThat(code(call(guest, HttpMethod.GET, s, null))).isEqualTo("FORBIDDEN");

        // Positions: Sales Manager (1) in Sales; Sales Officer (3) in the team, reporting to the manager.
        String manager = ok(call(owner, HttpMethod.POST, s + "/positions", position("Sales Manager", sales, null, 1))).path("data").path("id").asText();
        String officerPos = ok(call(owner, HttpMethod.POST, s + "/positions", position("Sales Officer", team, manager, 3))).path("data").path("id").asText();
        assertThat(code(call(owner, HttpMethod.PUT, s + "/positions/" + manager, position("Sales Manager", sales, officerPos, 1))))
                .isEqualTo("VALIDATION_FAILED");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/members/" + membershipOf(owner, org, owner) + "/placements", place(null, manager)));
        // A position in a unit places its holder in that unit; another unit is refused.
        String officerMembership = membershipOf(owner, org, officer);
        assertThat(code(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/members/" + officerMembership + "/placements", place(branch, officerPos))))
                .isEqualTo("VALIDATION_FAILED");
        JsonNode placed = ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/members/" + officerMembership + "/placements",
                Map.of("primary", Map.of("positionId", officerPos), "additional", List.of(Map.of("unitId", branch))))).path("data");
        assertThat(placed).hasSize(2);
        assertThat(placed.get(0).path("primary").asBoolean()).isTrue();
        assertThat(placed.get(0).path("unitId").asText()).isEqualTo(team);

        // The job title follows the position; HR's department is the nearest department; the manager the reports-to holder.
        JsonNode members = ok(call(officer, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data");
        for (JsonNode m : members) {
            if (m.path("userId").asText().equals(officer.id().toString())) {
                assertThat(m.path("title").asText()).isEqualTo("Sales Officer");
                assertThat(m.path("unit").asText()).isEqualTo("Sales Team A");
            }
        }
        JsonNode profile = ok(call(officer, HttpMethod.GET, "/api/orgs/" + org + "/hr/people/" + officer.id(), null)).path("data");
        assertThat(profile.path("department").asText()).isEqualTo("Sales");
        assertThat(profile.path("manager").path("id").asText()).isEqualTo(owner.id().toString());
        assertThat(profile.path("positionId").asText()).isEqualTo(officerPos);

        // Everyone but guests reads the structure, with headcount filled and people per unit.
        JsonNode view = ok(call(officer, HttpMethod.GET, s, null)).path("data");
        assertThat(view.path("canManage").asBoolean()).isFalse();
        assertThat(find(view.path("positions"), officerPos).path("filled").asInt()).isEqualTo(1);
        assertThat(find(view.path("positions"), officerPos).path("headcount").asInt()).isEqualTo(3);
        assertThat(find(view.path("units"), team).path("people").asInt()).isEqualTo(1);
        assertThat(find(view.path("units"), branch).path("people").asInt()).isEqualTo(1);

        // Renaming the position renames the job title; renaming the department renames HR's department.
        ok(call(owner, HttpMethod.PUT, s + "/positions/" + officerPos, position("Sales Executive", team, manager, 3)));
        ok(call(owner, HttpMethod.PUT, s + "/units/" + sales, salesIn.entrySet().stream().collect(HashMap::new,
                (m, e) -> m.put(e.getKey(), e.getKey().equals("name") ? "Sales & Marketing" : e.getValue()), HashMap::putAll)));
        profile = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/hr/people/" + officer.id(), null)).path("data");
        assertThat(profile.path("title").asText()).isEqualTo("Sales Executive");
        assertThat(profile.path("department").asText()).isEqualTo("Sales & Marketing");

        // A unit or position in use can't be archived or deleted; an unused one can.
        assertThat(code(call(owner, HttpMethod.POST, s + "/units/" + team + "/archive", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(owner, HttpMethod.POST, s + "/positions/" + officerPos + "/archive", null))).isEqualTo("INVALID_STATE");
        String spare = ok(call(owner, HttpMethod.POST, s + "/units", unit("Spare", "CENTER", branch))).path("data").path("id").asText();
        assertThat(ok(call(owner, HttpMethod.POST, s + "/units/" + spare + "/archive", null)).path("data").path("archived").asBoolean()).isTrue();
        assertThat(code(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/members/" + officerMembership + "/placements", place(spare, null))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(owner, HttpMethod.DELETE, s + "/units/" + spare, null));

        // HR's form can still send a department as text: it is matched to a department unit (or creates one).
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/hr/people/" + officer.id(), new HashMap<>(Map.of("department", "Finance", "title", "Accountant"))));
        view = ok(call(owner, HttpMethod.GET, s, null)).path("data");
        assertThat(view.path("units").findValuesAsText("name")).contains("Finance");
        profile = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/hr/people/" + officer.id(), null)).path("data");
        assertThat(profile.path("department").asText()).isEqualTo("Finance");
        assertThat(profile.path("title").asText()).isEqualTo("Accountant");
        // The additional place stays.
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members/" + officerMembership + "/placements", null)).path("data")).hasSize(2);
    }

    @Test
    void another_organisations_units_cannot_be_used() throws Exception {
        User a = signUp("Str A");
        User b = signUp("Str B");
        String orgA = newOrg(a);
        String orgB = newOrg(b);
        String foreign = ok(call(b, HttpMethod.POST, "/api/orgs/" + orgB + "/structure/units", unit("Theirs", "BRANCH", null)))
                .path("data").path("id").asText();
        assertThat(code(call(a, HttpMethod.POST, "/api/orgs/" + orgA + "/structure/units", unit("Mine", "TEAM", foreign)))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(a, HttpMethod.PUT, "/api/orgs/" + orgA + "/structure/units/" + foreign, unit("Mine", "TEAM", null)))).isEqualTo("NOT_FOUND");
        assertThat(code(call(a, HttpMethod.PUT, "/api/orgs/" + orgA + "/members/" + membershipOf(a, orgA, a) + "/placements", place(foreign, null))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(a, HttpMethod.PUT, "/api/orgs/" + orgA + "/members/" + UUID.randomUUID() + "/placements", place(null, null))))
                .isEqualTo("NOT_FOUND");
    }
}
