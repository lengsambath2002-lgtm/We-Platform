package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Custom roles: what they grant, organisation-wide or for one unit's people, and who may manage them. */
class CustomRoleTest extends IntegrationTest {

    private String membershipOf(User admin, String org, User who) throws Exception {
        for (JsonNode m : ok(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(who.id().toString())) {
                return m.path("membershipId").asText();
            }
        }
        throw new AssertionError("not a member");
    }

    private List<String> permissions(User u, String org) throws Exception {
        List<String> out = new java.util.ArrayList<>();
        ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions").forEach(p -> out.add(p.asText()));
        return out;
    }

    private String unit(User owner, String org, String name) throws Exception {
        Map<String, Object> in = new HashMap<>(Map.of("name", name, "kind", "BRANCH"));
        return ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/structure/units", in)).path("data").path("id").asText();
    }

    private void place(User owner, String org, User who, String unitId) throws Exception {
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/members/" + membershipOf(owner, org, who) + "/placements",
                Map.of("primary", Map.of("unitId", unitId), "additional", List.of())));
    }

    @Test
    void a_branch_hr_role_covers_that_branchs_people_only() throws Exception {
        User owner = signUp("Role Owner");
        User officer = signUp("Role Officer");
        User north = signUp("Role North");
        User south = signUp("Role South");
        User guest = signUp("Role Guest");
        String org = newOrg(owner);
        for (User u : List.of(officer, north, south)) {
            join(owner, org, u, "MEMBER");
        }
        join(owner, org, guest, "GUEST");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String n = unit(owner, org, "North branch");
        String s = unit(owner, org, "South branch");
        place(owner, org, officer, n);
        place(owner, org, north, n);
        place(owner, org, south, s);

        String roles = "/api/orgs/" + org + "/roles";
        // Only Owners and Admins manage roles; permissions come from the catalogue.
        assertThat(code(call(officer, HttpMethod.GET, roles, null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(owner, HttpMethod.POST, roles, Map.of("name", "Bad", "permissions", List.of("org.delete"))))).isEqualTo("VALIDATION_FAILED");
        String branchHr = ok(call(owner, HttpMethod.POST, roles, Map.of("name", "Branch HR", "permissions", List.of("hr.manage"))))
                .path("data").path("id").asText();
        assertThat(code(call(owner, HttpMethod.POST, roles, Map.of("name", "branch hr", "permissions", List.of("hr.manage"))))).isEqualTo("CONFLICT");
        // Members only: not guests, not Admins.
        assertThat(code(call(owner, HttpMethod.POST, roles + "/" + branchHr + "/grants", Map.of("membershipId", membershipOf(owner, org, guest)))))
                .isEqualTo("VALIDATION_FAILED");
        String grant = ok(call(owner, HttpMethod.POST, roles + "/" + branchHr + "/grants",
                Map.of("membershipId", membershipOf(owner, org, officer), "unitId", n))).path("data").path("id").asText();

        List<String> perms = permissions(officer, org);
        assertThat(perms).contains("hr.team").doesNotContain("hr.manage", "hr.payroll");

        String hr = "/api/orgs/" + org + "/hr";
        // The North branch's people: private details shown and editable; the South branch's: neither.
        JsonNode p = ok(call(officer, HttpMethod.GET, hr + "/people/" + north.id(), null)).path("data");
        assertThat(p.path("canEdit").asBoolean()).isTrue();
        assertThat(p.path("privateShown").asBoolean()).isTrue();
        assertThat(ok(call(officer, HttpMethod.GET, hr + "/people/" + south.id(), null)).path("data").path("canEdit").asBoolean()).isFalse();
        Map<String, Object> edit = new HashMap<>(Map.of("employmentType", "FULL_TIME", "unitId", n));
        ok(call(officer, HttpMethod.PUT, hr + "/people/" + north.id(), edit));
        Map<String, Object> southEdit = new HashMap<>(Map.of("employmentType", "FULL_TIME", "unitId", s));
        assertThat(code(call(officer, HttpMethod.PUT, hr + "/people/" + south.id(), southEdit))).isEqualTo("FORBIDDEN");
        // Moving someone to another unit needs an organisation-wide HR manager.
        assertThat(code(call(officer, HttpMethod.PUT, hr + "/people/" + north.id(), new HashMap<>(Map.of("unitId", s))))).isEqualTo("FORBIDDEN");
        // Never their own record.
        assertThat(ok(call(officer, HttpMethod.GET, hr + "/people/" + officer.id(), null)).path("data").path("canEdit").asBoolean()).isFalse();

        // The attendance board lists the branch's people; HR-wide pages and payroll stay closed.
        JsonNode board = ok(call(officer, HttpMethod.GET, hr + "/attendance/board", null)).path("data");
        assertThat(board.findValuesAsText("name")).contains("Role North").doesNotContain("Role South", "Role Officer");
        assertThat(code(call(officer, HttpMethod.GET, hr + "/dashboard", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(officer, HttpMethod.GET, hr + "/payroll/people", null))).isEqualTo("FORBIDDEN");

        // A unit with a role granted for it can't be deleted; revoking the role takes everything back.
        assertThat(code(call(owner, HttpMethod.DELETE, "/api/orgs/" + org + "/structure/units/" + n, null))).isEqualTo("INVALID_STATE");
        JsonNode members = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data");
        for (JsonNode m : members) {
            if (m.path("userId").asText().equals(officer.id().toString())) {
                assertThat(m.path("customRoles").get(0).asText()).isEqualTo("Branch HR");
            }
        }
        ok(call(owner, HttpMethod.DELETE, roles + "/grants/" + grant, null));
        assertThat(permissions(officer, org)).doesNotContain("hr.team");
        assertThat(code(call(officer, HttpMethod.PUT, hr + "/people/" + north.id(), edit))).isEqualTo("FORBIDDEN");
    }

    @Test
    void organisation_wide_roles_open_their_module() throws Exception {
        User owner = signUp("Role Owner2");
        User accountant = signUp("Role Accountant");
        User payroll = signUp("Role Payroll");
        User planner = signUp("Role Planner");
        String org = newOrg(owner);
        for (User u : List.of(accountant, payroll, planner)) {
            join(owner, org, u, "MEMBER");
        }
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String roles = "/api/orgs/" + org + "/roles";
        String product = "/api/orgs/" + org + "/business/products";
        Map<String, Object> bookkeeping = Map.of("nameEn", "Bookkeeping", "nameKm", "ការកត់ត្រាបញ្ជី", "unit", "month", "unitPrice", "150.00", "vat", true);
        assertThat(code(call(accountant, HttpMethod.POST, product, bookkeeping))).isEqualTo("FORBIDDEN");

        String finance = ok(call(owner, HttpMethod.POST, roles, Map.of("name", "Accountant", "permissions", List.of("business.manage"))))
                .path("data").path("id").asText();
        String someUnit = unit(owner, org, "Head office");
        // Organisation-wide permissions can't be limited to a unit.
        assertThat(code(call(owner, HttpMethod.POST, roles + "/" + finance + "/grants",
                Map.of("membershipId", membershipOf(owner, org, accountant), "unitId", someUnit)))).isEqualTo("VALIDATION_FAILED");
        ok(call(owner, HttpMethod.POST, roles + "/" + finance + "/grants", Map.of("membershipId", membershipOf(owner, org, accountant))));
        assertThat(permissions(accountant, org)).contains("business.manage");
        ok(call(accountant, HttpMethod.POST, product, bookkeeping));

        String pay = ok(call(owner, HttpMethod.POST, roles, Map.of("name", "Payroll officer", "permissions", List.of("hr.payroll"))))
                .path("data").path("id").asText();
        ok(call(owner, HttpMethod.POST, roles + "/" + pay + "/grants", Map.of("membershipId", membershipOf(owner, org, payroll))));
        ok(call(payroll, HttpMethod.GET, "/api/orgs/" + org + "/hr/payroll/people", null));
        assertThat(code(call(payroll, HttpMethod.GET, "/api/orgs/" + org + "/hr/dashboard", null))).isEqualTo("FORBIDDEN");

        String structure = ok(call(owner, HttpMethod.POST, roles, Map.of("name", "Org designer", "permissions",
                List.of("org.structure.manage", "activity.view")))).path("data").path("id").asText();
        assertThat(code(call(planner, HttpMethod.POST, "/api/orgs/" + org + "/structure/units", Map.of("name", "Lab", "kind", "CENTER"))))
                .isEqualTo("FORBIDDEN");
        ok(call(owner, HttpMethod.POST, roles + "/" + structure + "/grants", Map.of("membershipId", membershipOf(owner, org, planner))));
        ok(call(planner, HttpMethod.POST, "/api/orgs/" + org + "/structure/units", Map.of("name", "Lab", "kind", "CENTER")));
        ok(call(planner, HttpMethod.GET, "/api/orgs/" + org + "/activity", null));
        // Holding a role never lets someone manage roles.
        assertThat(code(call(planner, HttpMethod.GET, roles, null))).isEqualTo("FORBIDDEN");

        // Deleting a role takes it from everyone.
        ok(call(owner, HttpMethod.DELETE, roles + "/" + finance, null));
        assertThat(code(call(accountant, HttpMethod.POST, product, Map.of("nameEn", "Audit", "nameKm", "សវនកម្ម", "unit", "job",
                "unitPrice", "90.00", "vat", true)))).isEqualTo("FORBIDDEN");
    }
}
