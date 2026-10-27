package com.businessplatform.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Real API against a real PostgreSQL ({@code business_platform_test}, wiped and migrated once per
 * test context). Helpers sign people up and call endpoints with their token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.CleanDb.class)
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @TestConfiguration
    static class CleanDb {
        @Bean
        FlywayMigrationStrategy clean() {
            return f -> {
                f.clean();
                f.migrate();
            };
        }
    }

    protected record User(UUID id, String token, String email) {
    }

    protected User signUp(String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID().toString().substring(0, 8)
                + "@example.com";
        JsonNode r = call(null, HttpMethod.POST, "/api/auth/sign-up",
                Map.of("name", name, "email", email, "password", "Bright2026xyz"), "mobile");
        JsonNode d = r.path("data");
        return new User(UUID.fromString(d.path("user").path("id").asText()), d.path("accessToken").asText(), email);
    }

    protected String newOrg(User owner) throws Exception {
        String slug = "org-" + UUID.randomUUID().toString().substring(0, 8);
        ok(call(owner, HttpMethod.POST, "/api/orgs", Map.of("name", "Org " + slug, "slug", slug)));
        return slug;
    }

    /** Invites {@code who} with {@code role} and has them accept. */
    protected void join(User admin, String slug, User who, String role) throws Exception {
        JsonNode inv = ok(call(admin, HttpMethod.POST, "/api/orgs/" + slug + "/invitations",
                Map.of("email", who.email(), "role", role)));
        String link = inv.path("data").path("link").asText();
        ok(call(who, HttpMethod.POST, "/api/invitations/" + link.substring(link.lastIndexOf('/') + 1) + "/accept", null));
    }

    protected JsonNode call(User u, HttpMethod method, String path, Object body) throws Exception {
        return call(u, method, path, body, null);
    }

    protected JsonNode call(User u, HttpMethod method, String path, Object body, String client) throws Exception {
        var req = request(method, path);
        if (u != null) {
            req.header("Authorization", "Bearer " + u.token());
        }
        if (client != null) {
            req.header("X-Client", client);
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var res = mvc.perform(req).andReturn().getResponse();
        String text = res.getContentAsString();
        JsonNode node = text.isBlank() ? json.createObjectNode() : json.readTree(text);
        ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("_http", res.getStatus());
        return node;
    }

    protected static String code(JsonNode r) {
        return r.path("status").path("code").asText();
    }

    protected static JsonNode ok(JsonNode r) {
        if (!"SUCCESS".equals(code(r))) {
            throw new AssertionError("Expected SUCCESS but got " + r);
        }
        return r;
    }
}
