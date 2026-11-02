package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** "Export all our data" for owners, and "delete my account". */
class AccountDataTest extends IntegrationTest {

    private MockHttpServletResponse export(User u, String org) throws Exception {
        var started = mvc.perform(get("/api/orgs/" + org + "/export").header("Authorization", "Bearer " + u.token())).andReturn();
        return started.getRequest().isAsyncStarted() ? mvc.perform(asyncDispatch(started)).andReturn().getResponse() : started.getResponse();
    }

    @Test
    void owners_export_every_table_of_their_organisation_as_csv() throws Exception {
        User owner = signUp("Export Owner");
        User member = signUp("Export Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String other = newOrg(signUp("Other Owner"));
        String membership = null;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(member.id().toString())) {
                membership = m.path("membershipId").asText();
            }
        }
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/teams", Map.of("name", "=Sales, \"North\"", "membershipIds", List.of(membership))));
        assertThat(export(member, org).getStatus()).isEqualTo(403);

        MockHttpServletResponse res = export(owner, org);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("Content-Disposition")).contains(org + "-export-");
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(res.getContentAsByteArray()))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null;) {
                entries.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsKeys("README.txt", "data/organisation.csv", "data/people.csv", "data/team.csv",
                "data/team_member.csv", "data/membership.csv");
        assertThat(entries.get("data/people.csv")).contains(member.email()).contains(owner.email());
        // Quoted, and not run as a spreadsheet formula.
        assertThat(entries.get("data/team.csv")).contains("\"'=Sales, \"\"North\"\"\"");
        assertThat(entries.get("data/team_member.csv")).contains(membership);
        // Only this organisation; no secrets.
        assertThat(String.join("\n", entries.values())).doesNotContain(other).doesNotContain("token_hash").doesNotContain("password_hash");
        JsonNode log = ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/activity", null));
        assertThat(log.toString()).contains("org.exported");
    }

    @Test
    void deleting_an_account_erases_the_sign_in_and_keeps_the_organisation_records() throws Exception {
        User owner = signUp("Deleting Owner");
        User member = signUp("Deleting Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");

        // The primary owner hands over first.
        assertThat(ok(call(owner, HttpMethod.GET, "/api/me/delete/blockers", null)).path("data")).hasSize(1);
        assertThat(code(call(owner, HttpMethod.POST, "/api/me/delete", Map.of("password", "Bright2026xyz")))).isEqualTo("INVALID_STATE");

        assertThat(code(call(member, HttpMethod.POST, "/api/me/delete", Map.of("password", "wrong-password-9")))).isEqualTo("VALIDATION_FAILED");
        ok(call(member, HttpMethod.POST, "/api/me/delete", Map.of("password", "Bright2026xyz")));
        assertThat(mails.await(member.email(), 2).subject()).isEqualTo("Your Business Platform account was deleted");

        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in", Map.of("email", member.email(), "password", "Bright2026xyz"))))
                .isEqualTo("INVALID_CREDENTIALS");
        assertThat(call(member, HttpMethod.GET, "/api/me", null).path("_http").asInt()).isEqualTo(401);
        // The organisation keeps the person, suspended; the address can sign up again.
        boolean suspended = false;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(member.id().toString())) {
                suspended = m.path("status").asText().equals("SUSPENDED");
            }
        }
        assertThat(suspended).isTrue();
        ok(call(null, HttpMethod.POST, "/api/auth/sign-up", Map.of("name", "Back Again", "email", member.email(), "password", "Bright2026xyz")));
    }
}
