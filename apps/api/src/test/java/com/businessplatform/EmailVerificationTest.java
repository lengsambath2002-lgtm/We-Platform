package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.businessplatform.support.IntegrationTest;

/** A new account confirms its email before it creates an organisation or invites people. */
class EmailVerificationTest extends IntegrationTest {

    @DynamicPropertySource
    static void required(DynamicPropertyRegistry r) {
        r.add("platform.auth.require-verified-email", () -> "true");
    }

    @Test
    void the_emailed_link_confirms_the_address() throws Exception {
        User u = signUp("Verify Me");
        assertThat(ok(call(u, HttpMethod.GET, "/api/me", null)).path("data").path("emailVerified").asBoolean()).isFalse();
        assertThat(code(call(u, HttpMethod.POST, "/api/orgs", Map.of("name", "Too Soon", "slug", "too-soon-" + System.nanoTime()))))
                .isEqualTo("EMAIL_NOT_VERIFIED");

        String token = lastLink(u.email());
        // "Send again" retires the first link.
        ok(call(u, HttpMethod.POST, "/api/me/verify-email/resend", null));
        awaitCount(u.email(), 2);
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", token)))).isEqualTo("INVALID_TOKEN");
        ok(call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", lastLink(u.email()))));

        assertThat(ok(call(u, HttpMethod.GET, "/api/me", null)).path("data").path("emailVerified").asBoolean()).isTrue();
        ok(call(u, HttpMethod.POST, "/api/orgs", Map.of("name", "Now Then", "slug", "now-then-" + System.nanoTime())));
    }

    @Test
    void accepting_an_invitation_confirms_the_address_too() throws Exception {
        User owner = signUp("Verify Owner");
        verify(owner);
        String org = newOrg(owner);
        User guest = signUp("Verify Invitee");
        join(owner, org, guest, "MEMBER");
        assertThat(ok(call(guest, HttpMethod.GET, "/api/me", null)).path("data").path("emailVerified").asBoolean()).isTrue();
    }

    private void awaitCount(String email, int n) throws InterruptedException {
        java.time.Instant until = java.time.Instant.now().plusSeconds(5);
        while (mails.verifications(email).size() < n && java.time.Instant.now().isBefore(until)) {
            Thread.sleep(20);
        }
        assertThat(mails.verifications(email)).hasSize(n);
    }

    private void verify(User u) throws Exception {
        ok(call(null, HttpMethod.POST, "/api/auth/verify-email", Map.of("token", lastLink(u.email()))));
    }

    /** The token of the last "confirm your email" link sent to the address (the test mailer keeps what it sent). */
    private String lastLink(String email) throws InterruptedException {
        java.time.Instant until = java.time.Instant.now().plusSeconds(5);
        while (mails.verifications(email).isEmpty() && java.time.Instant.now().isBefore(until)) {
            Thread.sleep(20);
        }
        var sent = mails.verifications(email);
        assertThat(sent).isNotEmpty();
        Matcher m = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)").matcher(sent.get(sent.size() - 1).text());
        String token = null;
        while (m.find()) {
            token = m.group(1);
        }
        assertThat(token).isNotNull();
        return token;
    }
}
