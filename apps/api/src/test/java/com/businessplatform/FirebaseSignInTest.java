package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.identity.service.FirebaseTokens;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Sign-in with Google through Firebase: a new account, or linking the existing one with that email. */
@Import(FirebaseSignInTest.FakeFirebase.class)
class FirebaseSignInTest extends IntegrationTest {

    /** Tokens are "uid|email|verified|name" instead of signed JWTs. */
    @TestConfiguration
    static class FakeFirebase {
        @Bean
        @Primary
        FirebaseTokens fakeFirebaseTokens() {
            return new FirebaseTokens() {
                @Override
                public boolean configured() {
                    return true;
                }

                @Override
                public Identity verify(String token) {
                    String[] p = token.split("\\|");
                    if (p.length != 4) {
                        throw new BusinessException(ErrorCode.INVALID_TOKEN);
                    }
                    return new Identity(p[0], p[1], Boolean.parseBoolean(p[2]), p[3], "https://lh3.googleusercontent.com/a/x", "google.com");
                }
            };
        }
    }

    private JsonNode google(String token) throws Exception {
        return call(null, HttpMethod.POST, "/api/auth/firebase", Map.of("idToken", token), "mobile");
    }

    @Test
    void google_makes_an_account_or_links_the_one_with_that_email() throws Exception {
        String email = "google." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        JsonNode first = ok(google("uid-" + email + "|" + email + "|true|Google Person")).path("data");
        assertThat(first.path("user").path("name").asText()).isEqualTo("Google Person");
        assertThat(first.path("user").path("emailVerified").asBoolean()).isTrue();
        assertThat(first.path("user").path("avatarUrl").asText()).startsWith("https://");
        assertThat(ok(google("uid-" + email + "|" + email + "|true|Google Person")).path("data").path("user").path("id").asText())
                .isEqualTo(first.path("user").path("id").asText());

        // An email Google hasn't confirmed, a forged token, another Google account for a linked email.
        assertThat(code(google("x|" + email + "|false|X"))).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(code(google("not-a-token"))).isEqualTo("INVALID_TOKEN");
        assertThat(code(google("other-uid|" + email + "|true|X"))).isEqualTo("CONFLICT");
    }

    @Test
    void an_existing_password_account_is_linked_and_keeps_its_password() throws Exception {
        User u = signUp("Has Password");
        JsonNode linked = ok(google("uid-" + u.id() + "|" + u.email().toUpperCase() + "|true|Ignored Name")).path("data");
        assertThat(linked.path("user").path("id").asText()).isEqualTo(u.id().toString());
        assertThat(linked.path("user").path("name").asText()).isEqualTo("Has Password");
        // The password still works too.
        ok(call(null, HttpMethod.POST, "/api/auth/sign-in", Map.of("email", u.email(), "password", "Bright2026xyz"), "mobile"));
    }
}
