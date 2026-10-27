package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** One organisation never sees another's data; invitations only work for the invited email. */
class OrgIsolationTest extends IntegrationTest {

    @Test
    void an_outsider_gets_not_found_for_everything_in_another_organisation() throws Exception {
        User owner = signUp("Owner A");
        User outsider = signUp("Outsider B");
        String a = newOrg(owner);
        newOrg(outsider);
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + a + "/projects", Map.of("name", "Secret", "key", "SEC")));
        JsonNode task = ok(call(owner, HttpMethod.POST, "/api/orgs/" + a + "/projects/SEC/tasks", Map.of("title", "Hidden")));
        String taskId = task.path("data").path("id").asText();

        for (String path : new String[] { "/context", "/projects", "/projects/SEC", "/tasks/" + taskId, "/members",
                "/overview", "/approvals?box=sent", "/calendar?from=2026-11-01&to=2026-11-30" }) {
            assertThat(code(call(outsider, HttpMethod.GET, "/api/orgs/" + a + path, null))).as(path).isEqualTo("NOT_FOUND");
        }
        assertThat(code(call(outsider, HttpMethod.PATCH, "/api/orgs/" + a + "/tasks/" + taskId, Map.of("title", "x"))))
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void an_invitation_only_works_for_its_email_and_once() throws Exception {
        User owner = signUp("Owner Inv");
        User invited = signUp("Invited One");
        User other = signUp("Someone Else");
        String slug = newOrg(owner);
        JsonNode inv = ok(call(owner, HttpMethod.POST, "/api/orgs/" + slug + "/invitations",
                Map.of("email", invited.email(), "role", "MEMBER")));
        String link = inv.path("data").path("link").asText();
        String token = link.substring(link.lastIndexOf('/') + 1);
        assertThat(code(call(other, HttpMethod.POST, "/api/invitations/" + token + "/accept", null))).isEqualTo("FORBIDDEN");
        ok(call(invited, HttpMethod.POST, "/api/invitations/" + token + "/accept", null));
        assertThat(code(call(invited, HttpMethod.POST, "/api/invitations/" + token + "/accept", null)))
                .isEqualTo("INVALID_TOKEN");
        assertThat(ok(call(invited, HttpMethod.GET, "/api/orgs/" + slug + "/context", null))
                .path("data").path("org").path("role").asText()).isEqualTo("MEMBER");
    }

    @Test
    void members_cannot_manage_and_the_last_owner_stays() throws Exception {
        User owner = signUp("Only Owner");
        User member = signUp("Plain Member");
        String slug = newOrg(owner);
        join(owner, slug, member, "MEMBER");
        assertThat(code(call(member, HttpMethod.POST, "/api/orgs/" + slug + "/invitations",
                Map.of("email", "x@example.com", "role", "MEMBER")))).isEqualTo("FORBIDDEN");
        String ownerMembership = ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/members", null)).path("data")
                .findValues("membershipId").get(0).asText();
        assertThat(code(call(owner, HttpMethod.PATCH, "/api/orgs/" + slug + "/members/" + ownerMembership,
                Map.of("role", "MEMBER")))).isEqualTo("INVALID_STATE");
    }

    @Test
    void a_disabled_module_is_refused_by_the_api() throws Exception {
        User owner = signUp("Module Owner");
        String slug = newOrg(owner);
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + slug + "/modules/projects", Map.of("enabled", false)));
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/projects", null))).isEqualTo("MODULE_DISABLED");
        assertThat(code(call(owner, HttpMethod.PUT, "/api/orgs/" + slug + "/modules/education", Map.of("enabled", true))))
                .isEqualTo("INVALID_STATE");
    }
}
