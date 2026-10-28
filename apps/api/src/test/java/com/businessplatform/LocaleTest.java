package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Error messages follow {@code Accept-Language} ({@code km} or {@code kh} for Khmer, English otherwise). */
class LocaleTest extends IntegrationTest {

    @Test
    void a_refusal_and_its_field_come_back_in_khmer() throws Exception {
        User owner = signUp("Khmer Owner");
        String slug = newOrg(owner);
        Map<String, String> again = Map.of("name", "Second", "slug", slug);

        JsonNode km = callIn("km", owner, HttpMethod.POST, "/api/orgs", again);
        assertThat(code(km)).isEqualTo("CONFLICT");
        assertThat(km.path("error").path("message").asText()).isEqualTo("អាសយដ្ឋាននេះមានគេប្រើហើយ");
        assertThat(fieldMessages(km, "slug")).containsExactly("អាសយដ្ឋាននេះមានគេប្រើហើយ");

        assertThat(fieldMessages(callIn("kh", owner, HttpMethod.POST, "/api/orgs", again), "slug"))
                .containsExactly("អាសយដ្ឋាននេះមានគេប្រើហើយ");
        assertThat(fieldMessages(callIn("en", owner, HttpMethod.POST, "/api/orgs", again), "slug"))
                .containsExactly("This address is taken");
        assertThat(fieldMessages(call(owner, HttpMethod.POST, "/api/orgs", again), "slug"))
                .containsExactly("This address is taken");
    }

    @Test
    void validation_errors_come_back_in_khmer() throws Exception {
        Map<String, String> blankName = Map.of("name", "", "email", "blank@example.com", "password", "Bright2026xyz");

        JsonNode km = callIn("km", null, HttpMethod.POST, "/api/auth/sign-up", blankName);
        assertThat(code(km)).isEqualTo("VALIDATION_FAILED");
        assertThat(km.path("error").path("message").asText()).isEqualTo("ទិន្នន័យមិនត្រឹមត្រូវ");
        assertThat(fieldMessages(km, "name")).contains("ត្រូវតែបំពេញ");

        JsonNode en = call(null, HttpMethod.POST, "/api/auth/sign-up", blankName);
        assertThat(en.path("error").path("message").asText()).isEqualTo("Validation failed");
        assertThat(fieldMessages(en, "name")).contains("Required").allMatch(m -> m.equals("Required")
                || m.equals("2–80 characters"));

        // A custom message = "..." is translated as written.
        User owner = signUp("Pattern Owner");
        Map<String, String> badSlug = Map.of("name", "Bad Slug Org", "slug", "Bad Slug");
        assertThat(fieldMessages(callIn("km", owner, HttpMethod.POST, "/api/orgs", badSlug), "slug"))
                .containsExactly("អក្សរតូច លេខ ឬសញ្ញា (-) ពី 3 ដល់ 40 តួ");
        assertThat(fieldMessages(callIn("en", owner, HttpMethod.POST, "/api/orgs", badSlug), "slug"))
                .containsExactly("3–40 lowercase letters, numbers or dashes");
    }

    @Test
    void sign_up_keeps_the_request_language_and_only_en_or_km_are_accepted() throws Exception {
        User dara = signUpIn("km", "Sok Dara");
        assertThat(ok(call(dara, HttpMethod.GET, "/api/me", null)).path("data").path("locale").asText())
                .isEqualTo("km");

        User plain = signUp("Plain English");
        assertThat(ok(call(plain, HttpMethod.GET, "/api/me", null)).path("data").path("locale").asText())
                .isEqualTo("en");

        JsonNode fr = callIn("km", dara, HttpMethod.PATCH, "/api/me", Map.of("locale", "fr"));
        assertThat(code(fr)).isEqualTo("VALIDATION_FAILED");
        assertThat(fieldMessages(fr, "locale")).containsExactly("en ឬ km");
        assertThat(ok(call(dara, HttpMethod.PATCH, "/api/me", Map.of("locale", "en"))).path("data").path("locale")
                .asText()).isEqualTo("en");
    }

    private static List<String> fieldMessages(JsonNode r, String field) {
        List<String> out = new ArrayList<>();
        r.path("error").path("fields").forEach(f -> {
            if (field.equals(f.path("field").asText())) {
                out.add(f.path("message").asText());
            }
        });
        return out;
    }
}
