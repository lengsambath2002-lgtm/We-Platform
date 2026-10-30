package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The platform console: who may use it, suspending organisations, disabling accounts, granting super admin. */
class PlatformAdminTest extends IntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private User superAdmin(String name) throws Exception {
        User u = signUp(name);
        jdbc.update("UPDATE app_user SET super_admin = true WHERE id = ?", u.id());
        return u;
    }

    private String orgId(User owner, String slug) throws Exception {
        return ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null)).path("data").path("org").path("id").asText();
    }

    @Test
    void the_console_is_hidden_from_everyone_else() throws Exception {
        User owner = signUp("Plain Owner");
        newOrg(owner);
        for (String path : new String[] { "/api/platform/stats", "/api/platform/orgs", "/api/platform/users", "/api/platform/audit" }) {
            assertThat(code(call(owner, HttpMethod.GET, path, null))).as(path).isEqualTo("NOT_FOUND");
        }
        assertThat(call(null, HttpMethod.GET, "/api/platform/stats", null).path("_http").asInt()).isEqualTo(401);
        assertThat(ok(call(owner, HttpMethod.GET, "/api/me", null)).path("data").path("superAdmin").asBoolean()).isFalse();
    }

    @Test
    void a_listed_email_becomes_super_admin_when_it_signs_up() throws Exception {
        JsonNode r = ok(call(null, HttpMethod.POST, "/api/auth/sign-up",
                Map.of("name", "First Admin", "email", "first.admin@platform.test", "password", "Bright2026xyz"), "mobile"));
        assertThat(r.path("data").path("user").path("superAdmin").asBoolean()).isTrue();
        String token = r.path("data").path("accessToken").asText();
        var res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/platform/stats")
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void a_super_admin_sees_every_organisation_and_account_but_not_their_data() throws Exception {
        User root = superAdmin("Root Lister");
        User owner = signUp("Listed Owner");
        String slug = newOrg(owner);

        JsonNode orgs = ok(call(root, HttpMethod.GET, "/api/platform/orgs?q=" + slug, null)).path("data");
        assertThat(orgs.path("total").asLong()).isEqualTo(1);
        JsonNode row = orgs.path("items").get(0);
        assertThat(row.path("slug").asText()).isEqualTo(slug);
        assertThat(row.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(row.path("members").asLong()).isEqualTo(1);
        assertThat(row.path("owners").get(0).path("email").asText()).isEqualTo(owner.email());
        assertThat(row.path("modules").toString()).contains("projects");

        JsonNode users = ok(call(root, HttpMethod.GET, "/api/platform/users?q=" + owner.email(), null)).path("data");
        assertThat(users.path("items").get(0).path("organisations").asLong()).isEqualTo(1);
        JsonNode stats = ok(call(root, HttpMethod.GET, "/api/platform/stats", null)).path("data");
        assertThat(stats.path("organisations").asLong()).isPositive();
        assertThat(stats.path("superAdmins").asLong()).isPositive();

        // Being a super admin is not membership: the organisation itself stays closed to them.
        assertThat(code(call(root, HttpMethod.GET, "/api/orgs/" + slug + "/projects", null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void a_suspended_organisation_is_closed_to_its_members_until_reactivated() throws Exception {
        User root = superAdmin("Root Suspender");
        User owner = signUp("Suspended Owner");
        String slug = newOrg(owner);
        String id = orgId(owner, slug);

        assertThat(code(call(root, HttpMethod.POST, "/api/platform/orgs/" + id + "/suspend", Map.of("reason", " "))))
                .isEqualTo("VALIDATION_FAILED");
        JsonNode d = ok(call(root, HttpMethod.POST, "/api/platform/orgs/" + id + "/suspend", Map.of("reason", "Unpaid invoice")))
                .path("data");
        assertThat(d.path("org").path("status").asText()).isEqualTo("SUSPENDED");
        assertThat(d.path("suspendedReason").asText()).isEqualTo("Unpaid invoice");
        JsonNode refused = call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null);
        assertThat(code(refused)).isEqualTo("ORG_SUSPENDED");
        assertThat(refused.path("_http").asInt()).isEqualTo(403);
        // Still listed for its members, so they can see it and why they can't open it.
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs", null)).path("data").toString()).contains(slug);

        ok(call(root, HttpMethod.POST, "/api/platform/orgs/" + id + "/reactivate", null));
        ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null));

        JsonNode audit = ok(call(root, HttpMethod.GET, "/api/platform/audit?target=" + id, null)).path("data").path("items");
        assertThat(audit.get(0).path("action").asText()).isEqualTo("org.reactivated");
        assertThat(audit.get(1).path("action").asText()).isEqualTo("org.suspended");
        assertThat(audit.get(1).path("data").path("reason").asText()).isEqualTo("Unpaid invoice");
        assertThat(audit.get(1).path("actor").path("id").asText()).isEqualTo(root.id().toString());
        assertThat(audit.get(1).path("targetName").asText()).isEqualTo("Org " + slug);
    }

    @Test
    void a_deleted_organisation_can_be_restored() throws Exception {
        User root = superAdmin("Root Restorer");
        User owner = signUp("Deleting Owner");
        String slug = newOrg(owner);
        String id = orgId(owner, slug);
        ok(call(owner, HttpMethod.DELETE, "/api/orgs/" + slug, Map.of("name", "Org " + slug)));
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(root, HttpMethod.POST, "/api/platform/orgs/" + id + "/suspend", Map.of("reason", "x"))))
                .isEqualTo("INVALID_STATE");

        ok(call(root, HttpMethod.POST, "/api/platform/orgs/" + id + "/restore", null));
        ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null));
    }

    @Test
    void a_super_admin_switches_an_organisations_modules_and_the_org_audit_log_shows_it() throws Exception {
        User root = superAdmin("Root Modules");
        User owner = signUp("Modules Owner");
        String slug = newOrg(owner);
        String id = orgId(owner, slug);
        JsonNode d = ok(call(root, HttpMethod.PUT, "/api/platform/orgs/" + id + "/modules/business", Map.of("enabled", true))).path("data");
        assertThat(d.path("org").path("modules").toString()).contains("business");
        JsonNode ctx = ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null)).path("data");
        assertThat(ctx.path("permissions").toString()).contains("business.view");
        JsonNode log = ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/activity?module=org", null)).path("data").path("items");
        assertThat(log.get(0).path("verb").asText()).isEqualTo("org.module.enabled");
        assertThat(log.get(0).path("actor").path("id").asText()).isEqualTo(root.id().toString());
    }

    @Test
    void a_disabled_account_is_signed_out_at_once_and_cannot_sign_in_until_enabled() throws Exception {
        User root = superAdmin("Root Disabler");
        User person = signUp("Disabled Person");
        ok(call(person, HttpMethod.GET, "/api/me", null));

        assertThat(code(call(root, HttpMethod.POST, "/api/platform/users/" + root.id() + "/disable", Map.of("reason", "x"))))
                .isEqualTo("INVALID_STATE");
        JsonNode d = ok(call(root, HttpMethod.POST, "/api/platform/users/" + person.id() + "/disable",
                Map.of("reason", "Spam sign-ups"))).path("data");
        assertThat(d.path("user").path("status").asText()).isEqualTo("DISABLED");
        assertThat(d.path("signedInDevices").asInt()).isZero();

        assertThat(call(person, HttpMethod.GET, "/api/me", null).path("_http").asInt()).isEqualTo(401);
        Map<String, String> creds = Map.of("email", person.email(), "password", "Bright2026xyz");
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in", creds, "mobile"))).isEqualTo("ACCOUNT_DISABLED");
        // A wrong password still answers as before: the account's state is not revealed.
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in",
                Map.of("email", person.email(), "password", "Wrong2026xyz"), "mobile"))).isEqualTo("INVALID_CREDENTIALS");

        ok(call(root, HttpMethod.POST, "/api/platform/users/" + person.id() + "/enable", null));
        ok(call(null, HttpMethod.POST, "/api/auth/sign-in", creds, "mobile"));
    }

    @Test
    void signing_an_account_out_everywhere_and_sending_it_a_password_reset() throws Exception {
        User root = superAdmin("Root Support");
        User person = signUp("Support Person");
        ok(call(root, HttpMethod.POST, "/api/platform/users/" + person.id() + "/sign-out", null));
        assertThat(call(person, HttpMethod.GET, "/api/me", null).path("_http").asInt()).isEqualTo(401);

        ok(call(root, HttpMethod.POST, "/api/platform/users/" + person.id() + "/password-reset", null));
        assertThat(mails.await(person.email(), 1).text()).contains("/reset-password?token=");
    }

    @Test
    void super_admin_is_granted_and_revoked_but_never_by_yourself() throws Exception {
        User root = superAdmin("Root Granter");
        User other = signUp("Next Admin");
        assertThat(code(call(root, HttpMethod.PUT, "/api/platform/users/" + root.id() + "/super-admin", Map.of("enabled", false))))
                .isEqualTo("INVALID_STATE");

        ok(call(root, HttpMethod.PUT, "/api/platform/users/" + other.id() + "/super-admin", Map.of("enabled", true)));
        assertThat(ok(call(other, HttpMethod.GET, "/api/me", null)).path("data").path("superAdmin").asBoolean()).isTrue();
        ok(call(other, HttpMethod.GET, "/api/platform/stats", null));
        // A super admin can't be disabled while they are one.
        assertThat(code(call(root, HttpMethod.POST, "/api/platform/users/" + other.id() + "/disable", Map.of("reason", "x"))))
                .isEqualTo("INVALID_STATE");

        ok(call(other, HttpMethod.PUT, "/api/platform/users/" + root.id() + "/super-admin", Map.of("enabled", false)));
        assertThat(code(call(root, HttpMethod.GET, "/api/platform/stats", null))).isEqualTo("NOT_FOUND");
    }
}
