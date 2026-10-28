package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Password change, forgot / reset password, and the signed-in devices. */
class AccountTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private record Device(User user, String refreshToken) {
    }

    /** Signs {@code u} in once more (another device), with the given password. */
    private Device signIn(User u, String password) throws Exception {
        JsonNode d = ok(call(null, HttpMethod.POST, "/api/auth/sign-in",
                Map.of("email", u.email(), "password", password), "mobile")).path("data");
        return new Device(u.withToken(d.path("accessToken").asText()), d.path("refreshToken").asText());
    }

    private int status(User u, String path) throws Exception {
        return call(u, HttpMethod.GET, path, null).path("_http").asInt();
    }

    private static String token(MailMessage m) {
        String text = m.text();
        int at = text.indexOf("token=") + "token=".length();
        return text.substring(at, text.indexOf('\n', at)).strip();
    }

    @Test
    void changing_the_password_needs_the_current_one_and_signs_out_other_devices() throws Exception {
        User u = signUp("Change Pass");
        Device other = signIn(u, "Bright2026xyz");

        JsonNode wrong = call(u, HttpMethod.POST, "/api/me/password",
                Map.of("currentPassword", "Wrong2026xyz", "newPassword", "Fresh2026abc"));
        assertThat(code(wrong)).isEqualTo("VALIDATION_FAILED");
        assertThat(wrong.path("error").path("fields").get(0).path("field").asText()).isEqualTo("currentPassword");
        assertThat(code(call(u, HttpMethod.POST, "/api/me/password",
                Map.of("currentPassword", "Bright2026xyz", "newPassword", "short")))).isEqualTo("VALIDATION_FAILED");

        ok(call(u, HttpMethod.POST, "/api/me/password",
                Map.of("currentPassword", "Bright2026xyz", "newPassword", "Fresh2026abc")));
        // This device stays signed in; the other one is out at once (access and refresh token).
        assertThat(status(u, "/api/me")).isEqualTo(200);
        assertThat(status(other.user(), "/api/me")).isEqualTo(401);
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", other.refreshToken()),
                "mobile"))).isEqualTo("INVALID_TOKEN");
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/sign-in",
                Map.of("email", u.email(), "password", "Bright2026xyz")))).isEqualTo("INVALID_CREDENTIALS");
        signIn(u, "Fresh2026abc");
        assertThat(mails.await(u.email(), 1).subject()).isEqualTo("Your Business Platform password was changed");
    }

    @Test
    void forgot_password_answers_the_same_for_unknown_emails() throws Exception {
        JsonNode r = ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", "nobody.here@example.com")));
        assertThat(r.path("_http").asInt()).isEqualTo(200);
        Thread.sleep(200);
        assertThat(mails.to("nobody.here@example.com")).isEmpty();
    }

    @Test
    void a_reset_link_works_once_signs_out_every_device_and_signs_in() throws Exception {
        User u = signUp("Forgot Pass");
        Device other = signIn(u, "Bright2026xyz");
        ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", u.email().toUpperCase())));
        MailMessage m = mails.await(u.email(), 1);
        assertThat(m.subject()).isEqualTo("Reset your Business Platform password");
        assertThat(m.html()).contains("/reset-password?token=");
        String token = token(m);

        assertThat(ok(call(null, HttpMethod.POST, "/api/auth/reset-password/check", Map.of("token", token)))
                .path("data").path("valid").asBoolean()).isTrue();
        // A weak password is refused without using up the link.
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", token, "password", "weakweakweak")))).isEqualTo("VALIDATION_FAILED");

        JsonNode d = ok(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", token, "password", "Renewed2026x"), "mobile")).path("data");
        User fresh = u.withToken(d.path("accessToken").asText());
        assertThat(status(fresh, "/api/me")).isEqualTo(200);
        assertThat(status(u, "/api/me")).isEqualTo(401);
        assertThat(status(other.user(), "/api/me")).isEqualTo(401);

        assertThat(code(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", token, "password", "Another2026x")))).isEqualTo("INVALID_TOKEN");
        assertThat(ok(call(null, HttpMethod.POST, "/api/auth/reset-password/check", Map.of("token", token)))
                .path("data").path("valid").asBoolean()).isFalse();
        signIn(u, "Renewed2026x");
    }

    @Test
    void a_reset_link_expires_and_a_newer_one_replaces_it() throws Exception {
        User u = signUpIn("km", "Expire Pass");
        ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", u.email())));
        MailMessage first = mails.await(u.email(), 1);
        // In the account's language.
        assertThat(first.subject()).isEqualTo("កំណត់ពាក្យសម្ងាត់ Business Platform របស់អ្នកឡើងវិញ");
        ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", u.email())));
        String second = token(mails.await(u.email(), 2));
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", token(first), "password", "Renewed2026x")))).isEqualTo("INVALID_TOKEN");

        jdbc.update("UPDATE password_reset SET expires_at = now() - interval '1 minute' WHERE user_id = ?", u.id());
        assertThat(code(call(null, HttpMethod.POST, "/api/auth/reset-password",
                Map.of("token", second, "password", "Renewed2026x")))).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void forgot_password_is_rate_limited_per_email() throws Exception {
        User u = signUp("Spam Pass");
        for (int i = 0; i < 5; i++) {
            ok(call(null, HttpMethod.POST, "/api/auth/forgot-password", Map.of("email", u.email())));
        }
        mails.await(u.email(), 3);
        Thread.sleep(200);
        assertThat(mails.to(u.email())).hasSize(3);
    }

    @Test
    void devices_are_listed_and_signed_out_one_by_one_or_all_others() throws Exception {
        User u = signUp("Many Devices");
        Device b = signIn(u, "Bright2026xyz");
        JsonNode list = ok(call(u, HttpMethod.GET, "/api/me/sessions", null)).path("data");
        assertThat(list).hasSize(2);
        String bId = null;
        for (JsonNode s : list) {
            if (!s.path("current").asBoolean()) {
                bId = s.path("id").asText();
            }
        }
        assertThat(bId).isNotNull();

        // A refresh keeps the device (same id), it doesn't add one.
        JsonNode r = ok(call(null, HttpMethod.POST, "/api/auth/refresh", Map.of("refreshToken", b.refreshToken()), "mobile"));
        User b2 = u.withToken(r.path("data").path("accessToken").asText());
        assertThat(ok(call(u, HttpMethod.GET, "/api/me/sessions", null)).path("data")).hasSize(2);
        assertThat(ok(call(b2, HttpMethod.GET, "/api/me/sessions", null)).path("data").toString()).contains(bId);

        // Someone else can't sign out my device.
        User stranger = signUp("Stranger");
        assertThat(code(call(stranger, HttpMethod.DELETE, "/api/me/sessions/" + bId, null))).isEqualTo("NOT_FOUND");

        ok(call(u, HttpMethod.DELETE, "/api/me/sessions/" + bId, null));
        assertThat(status(b2, "/api/me")).isEqualTo(401);
        assertThat(ok(call(u, HttpMethod.GET, "/api/me/sessions", null)).path("data")).hasSize(1);

        Device c = signIn(u, "Bright2026xyz");
        Device d = signIn(u, "Bright2026xyz");
        assertThat(ok(call(u, HttpMethod.POST, "/api/me/sessions/sign-out-others", null)).path("data").path("count").asInt())
                .isEqualTo(2);
        assertThat(status(c.user(), "/api/me")).isEqualTo(401);
        assertThat(status(d.user(), "/api/me")).isEqualTo(401);
        assertThat(status(u, "/api/me")).isEqualTo(200);
        JsonNode left = ok(call(u, HttpMethod.GET, "/api/me/sessions", null)).path("data");
        assertThat(left).hasSize(1);
        assertThat(left.get(0).path("current").asBoolean()).isTrue();
    }

    @Test
    void signing_out_ends_the_access_token_too() throws Exception {
        User u = signUp("Sign Out");
        Device b = signIn(u, "Bright2026xyz");
        ok(call(null, HttpMethod.POST, "/api/auth/sign-out", Map.of("refreshToken", b.refreshToken()), "mobile"));
        assertThat(status(b.user(), "/api/me")).isEqualTo(401);
        assertThat(status(u, "/api/me")).isEqualTo(200);
    }
}
