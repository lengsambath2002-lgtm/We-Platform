package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

class AuthTest extends IntegrationTest {

    @Test
    void sign_up_rejects_weak_passwords_and_taken_emails() throws Exception {
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-up",
                Map.of("name", "Weak One", "email", "weak@example.com", "password", "short1")))).isEqualTo("VALIDATION_FAILED");
        User u = signUp("Taken Name");
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-up",
                Map.of("name", "Again", "email", u.email(), "password", "Bright2026xyz")))).isEqualTo("CONFLICT");
    }

    @Test
    void five_wrong_passwords_lock_the_account() throws Exception {
        User u = signUp("Lock Me");
        for (int i = 0; i < 5; i++) {
            assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in",
                    Map.of("email", u.email(), "password", "Wrong2026xyz")))).isEqualTo("INVALID_CREDENTIALS");
        }
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in",
                Map.of("email", u.email(), "password", "Bright2026xyz")))).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void a_refresh_token_works_once_and_its_reuse_ends_every_session() throws Exception {
        User u = signUp("Rotate Me");
        JsonNode in = ok(call(null, HttpMethod.POST, "/api/auth/sign-in",
                Map.of("email", u.email(), "password", "Bright2026xyz"), "mobile"));
        String first = in.path("data").path("refreshToken").asText();
        JsonNode next = ok(call(null, HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", first), "mobile"));
        String second = next.path("data").path("refreshToken").asText();
        assertThat(second).isNotEqualTo(first);
        // The old one again: refused, and the new one stops working too.
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", first), "mobile")))
                .isEqualTo("INVALID_TOKEN");
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", second), "mobile")))
                .isEqualTo("INVALID_TOKEN");
    }

    @Test
    void the_api_needs_a_token() throws Exception {
        assertThat(call(null, HttpMethod.GET, "/api/me", null).path("_http").asInt()).isEqualTo(401);
    }
}
