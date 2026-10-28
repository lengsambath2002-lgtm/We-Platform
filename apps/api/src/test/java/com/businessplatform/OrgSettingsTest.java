package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Invitation emails, and renaming, leaving and deleting an organisation. */
class OrgSettingsTest extends IntegrationTest {

    private static String tokenOf(String link) {
        return link.substring(link.lastIndexOf('/') + 1);
    }

    @Test
    void an_invitation_is_emailed_in_the_inviters_language_and_can_be_resent() throws Exception {
        User owner = signUpIn("km", "Khmer Owner");
        String org = newOrg(owner);
        String email = "new.person." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode inv = ok(callIn("km", owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations",
                Map.of("email", email, "role", "MEMBER"))).path("data");
        assertThat(inv.path("emailed").asBoolean()).isTrue();
        String link = inv.path("link").asText();
        MailMessage m = mails.await(email, 1);
        assertThat(m.subject()).isEqualTo("Khmer Owner បានអញ្ជើញអ្នកចូលរួមជាមួយ Org " + org + " នៅលើ Business Platform");
        assertThat(m.text()).contains(link).contains("សមាជិក");
        assertThat(m.html()).contains(link);

        JsonNode again = ok(call(owner, HttpMethod.POST,
                "/api/orgs/" + org + "/invitations/" + inv.path("id").asText() + "/resend", null)).path("data");
        String newLink = again.path("link").asText();
        assertThat(newLink).isNotEqualTo(link);
        assertThat(mails.await(email, 2).text()).contains(newLink);
        assertThat(code(call(null, HttpMethod.GET, "/api/invitations/" + tokenOf(link), null))).isEqualTo("INVALID_TOKEN");
        ok(call(null, HttpMethod.GET, "/api/invitations/" + tokenOf(newLink), null));
    }

    @Test
    void an_invitee_with_an_account_gets_the_email_in_their_language() throws Exception {
        User owner = signUp("English Owner");
        String org = newOrg(owner);
        User invitee = signUpIn("km", "Khmer Invitee");
        ok(callIn("en", owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations",
                Map.of("email", invitee.email(), "role", "ADMIN")));
        assertThat(mails.await(invitee.email(), 1).text()).contains("English Owner បានអញ្ជើញអ្នក").contains("អ្នកគ្រប់គ្រង");
    }

    @Test
    void only_admins_rename_and_the_web_address_stays() throws Exception {
        User owner = signUp("Rename Owner");
        User member = signUp("Rename Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        assertThat(code(call(member, HttpMethod.PATCH, "/api/orgs/" + org, Map.of("name", "Taken Over")))).isEqualTo("FORBIDDEN");
        JsonNode r = ok(call(owner, HttpMethod.PATCH, "/api/orgs/" + org, Map.of("name", "Renamed Org"))).path("data");
        assertThat(r.path("name").asText()).isEqualTo("Renamed Org");
        assertThat(r.path("slug").asText()).isEqualTo(org);
    }

    @Test
    void the_last_owner_cannot_leave_but_members_can() throws Exception {
        User owner = signUp("Leave Owner");
        User member = signUp("Leave Member");
        User admin = signUp("Leave Admin");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        join(owner, org, admin, "ADMIN");

        JsonNode refused = call(owner, HttpMethod.POST, "/api/orgs/" + org + "/leave", null);
        assertThat(code(refused)).isEqualTo("INVALID_STATE");
        assertThat(refused.path("error").path("message").asText()).contains("only owner");

        ok(call(member, HttpMethod.POST, "/api/orgs/" + org + "/leave", null));
        assertThat(code(call(member, HttpMethod.GET, "/api/orgs/" + org + "/context", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(member, HttpMethod.GET, "/api/orgs", null)).path("data")).isEmpty();

        // With a second owner, the first may go.
        String adminMembership = null;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(admin.id().toString())) {
                adminMembership = m.path("membershipId").asText();
            }
        }
        ok(call(owner, HttpMethod.PATCH, "/api/orgs/" + org + "/members/" + adminMembership, Map.of("role", "OWNER")));
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/leave", null));
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null))).isEqualTo("NOT_FOUND");
        ok(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/context", null));
        // Someone who left can be invited back.
        join(admin, org, member, "GUEST");
        assertThat(ok(call(member, HttpMethod.GET, "/api/orgs/" + org + "/context", null))
                .path("data").path("org").path("role").asText()).isEqualTo("GUEST");
    }

    @Test
    void only_owners_delete_with_the_name_and_then_the_org_is_gone_for_everyone() throws Exception {
        User owner = signUp("Delete Owner");
        User admin = signUp("Delete Admin");
        String org = newOrg(owner);
        join(owner, org, admin, "ADMIN");
        String name = "Org " + org;
        JsonNode pending = ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations",
                Map.of("email", "later." + org + "@example.com", "role", "MEMBER"))).path("data");

        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions")
                .toString()).contains("org.delete");
        assertThat(ok(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions")
                .toString()).doesNotContain("org.delete");
        assertThat(code(call(admin, HttpMethod.DELETE, "/api/orgs/" + org, Map.of("name", name)))).isEqualTo("FORBIDDEN");
        assertThat(code(call(owner, HttpMethod.DELETE, "/api/orgs/" + org, Map.of("name", "Something else"))))
                .isEqualTo("VALIDATION_FAILED");

        ok(call(owner, HttpMethod.DELETE, "/api/orgs/" + org, Map.of("name", name)));
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/members", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs", null)).path("data")).isEmpty();
        assertThat(code(call(null, HttpMethod.GET, "/api/invitations/" + tokenOf(pending.path("link").asText()), null)))
                .isEqualTo("INVALID_TOKEN");
        // The web address stays taken.
        assertThat(code(call(owner, HttpMethod.POST, "/api/orgs", Map.of("name", "Again", "slug", org)))).isEqualTo("CONFLICT");
    }
}
