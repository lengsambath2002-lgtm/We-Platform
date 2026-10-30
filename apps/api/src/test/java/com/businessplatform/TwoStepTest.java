package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Two-step sign-in: set up with an authenticator app, sign in with a code or a recovery code, turn it off. */
class TwoStepTest extends IntegrationTest {

    private static final String PASSWORD = "Correct-horse-42";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sign_in_asks_for_a_code_once_two_step_is_on() throws Exception {
        User u = signUp("Two Step");
        JsonNode setup = ok(call(u, HttpMethod.POST, "/api/me/two-step/setup", null)).path("data");
        String secret = setup.path("secret").asText();
        assertThat(setup.path("uri").asText()).startsWith("otpauth://totp/Business%20Platform:").contains("secret=" + secret);

        assertThat(code(call(u, HttpMethod.POST, "/api/me/two-step/enable", Map.of("code", "000000")))).isEqualTo("VALIDATION_FAILED");
        long step = System.currentTimeMillis() / 30_000;
        List<String> recovery = new ArrayList<>();
        ok(call(u, HttpMethod.POST, "/api/me/two-step/enable", Map.of("code", totp(secret, step)))).path("data").path("codes")
                .forEach(c -> recovery.add(c.asText()));
        assertThat(recovery).hasSize(10);
        assertThat(mails.await(u.email(), 1).subject()).isEqualTo("Two-step sign-in is on for your Business Platform account");
        assertThat(ok(call(u, HttpMethod.GET, "/api/me/two-step", null)).path("data").path("recoveryCodesLeft").asInt()).isEqualTo(10);

        // The password alone gives a challenge, not a session.
        JsonNode first = ok(signIn(u));
        assertThat(first.path("data").path("accessToken").isTextual()).isFalse();
        String challenge = first.path("data").path("twoStep").asText();
        // The code used to turn it on doesn't work twice; the next one does.
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", challenge, "code", totp(secret, step)))))
                .isEqualTo("VALIDATION_FAILED");
        JsonNode s = ok(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", challenge, "code", totp(secret, step + 1)), "mobile"));
        assertThat(s.path("data").path("accessToken").asText()).isNotBlank();
        // A challenge works once.
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", challenge, "code", recovery.get(0)))))
                .isEqualTo("INVALID_TOKEN");

        // A recovery code works once, in place of the app.
        ok(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", ok(signIn(u)).path("data").path("twoStep").asText(),
                "code", recovery.get(0).toUpperCase())));
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", ok(signIn(u)).path("data").path("twoStep").asText(),
                "code", recovery.get(0))))).isEqualTo("VALIDATION_FAILED");

        // A password reset link replaces the password, not the code.
        ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", u.email())));
        MailMessage m = mails.await(u.email(), 2);
        String text = m.text();
        int at = text.indexOf("token=") + "token=".length();
        JsonNode reset = ok(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", text.substring(at, text.indexOf('\n', at)).strip(), "password", PASSWORD), "mobile"));
        assertThat(reset.path("data").path("twoStep").asText()).isNotBlank();
        assertThat(reset.path("data").path("accessToken").isTextual()).isFalse();
        // The reset signed every device out: back in with a recovery code.
        u = u.withToken(ok(call(null, HttpMethod.POST, "/api/auth/two-step", Map.of("challenge", reset.path("data").path("twoStep").asText(),
                "code", recovery.get(2)), "mobile")).path("data").path("accessToken").asText());

        // Off with the password and a code: the password alone signs in again.
        assertThat(code(call(u, HttpMethod.POST, "/api/me/two-step/disable", Map.of("password", "wrong-password-1", "code", recovery.get(1)))))
                .isEqualTo("VALIDATION_FAILED");
        ok(call(u, HttpMethod.POST, "/api/me/two-step/disable", Map.of("password", PASSWORD, "code", recovery.get(1))));
        assertThat(ok(call(null, HttpMethod.POST, "/api/auth/sign-in", Map.of("email", u.email(), "password", PASSWORD), "mobile"))
                .path("data").path("accessToken").asText()).isNotBlank();
    }

    @Test
    void a_super_admin_turns_it_off_for_someone_who_lost_their_phone() throws Exception {
        User root = signUp("Two Step Root");
        jdbc.update("UPDATE app_user SET super_admin = true WHERE id = ?", root.id());
        User u = signUp("Lost Phone");
        String secret = ok(call(u, HttpMethod.POST, "/api/me/two-step/setup", null)).path("data").path("secret").asText();
        ok(call(u, HttpMethod.POST, "/api/me/two-step/enable", Map.of("code", totp(secret, System.currentTimeMillis() / 30_000))));
        assertThat(code(call(u, HttpMethod.POST, "/api/platform/users/" + u.id() + "/two-step/reset", null))).isEqualTo("NOT_FOUND");

        JsonNode d = ok(call(root, HttpMethod.POST, "/api/platform/users/" + u.id() + "/two-step/reset", null)).path("data");
        assertThat(d.path("twoStep").asBoolean()).isFalse();
        assertThat(mails.await(u.email(), 2).subject()).isEqualTo("Two-step sign-in was turned off for your Business Platform account");
        assertThat(ok(signIn(u)).path("data").path("accessToken").asText()).isNotBlank();
        assertThat(code(call(root, HttpMethod.POST, "/api/platform/users/" + u.id() + "/two-step/reset", null))).isEqualTo("INVALID_STATE");
    }

    private JsonNode signIn(User u) throws Exception {
        return call(null, HttpMethod.POST, "/api/auth/sign-in", Map.of("email", u.email(), "password", "Bright2026xyz"), "mobile");
    }

    /** What an authenticator app shows for the step (RFC 6238, as the API computes it). */
    private static String totp(String base32, long step) throws Exception {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream key = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : base32.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                key.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.toByteArray(), "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int o = h[h.length - 1] & 0x0f;
        int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
        return String.format("%06d", bin % 1_000_000);
    }
}
