package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Without PLATFORM_VIRTUALBIZ_URL (the default test settings) Retail & Commerce cannot be used. */
class RetailNotConfiguredTest extends IntegrationTest {

    @Test
    void retail_is_refused_with_a_clear_message_when_virtualbiz_is_not_configured() throws Exception {
        User owner = signUp("Unconfigured Owner");
        String slug = newOrg(owner);
        JsonNode r = call(owner, HttpMethod.PUT, "/api/orgs/" + slug + "/modules/retail", Map.of("enabled", true));
        assertThat(code(r)).isEqualTo("INVALID_STATE");
        assertThat(r.path("error").path("fields").get(0).path("message").asText()).contains("PLATFORM_VIRTUALBIZ_URL");
        JsonNode km = callIn("km", owner, HttpMethod.PUT, "/api/orgs/" + slug + "/modules/retail", Map.of("enabled", true));
        assertThat(km.path("error").path("fields").get(0).path("message").asText()).contains("VirtualBiz").contains("លក់រាយ");

        JsonNode ctx = ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/context", null)).path("data");
        for (JsonNode m : ctx.path("modules")) {
            if (m.path("key").asText().equals("retail")) {
                assertThat(m.path("available").asBoolean()).isFalse();
                assertThat(m.path("enabled").asBoolean()).isFalse();
            }
        }
        assertThat(ctx.path("permissions").toString()).doesNotContain("retail.");
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/retail/shops", null))).isEqualTo("MODULE_DISABLED");
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/modules", null)).path("data")) {
            if (m.path("key").asText().equals("retail")) {
                assertThat(m.path("status").asText()).isEqualTo("COMING_SOON");
            }
        }
    }

    @Test
    void the_local_development_client_exists_only_locally() throws Exception {
        User u = signUp("Local Dev");
        Map<String, String> q = Map.of("response_type", "code", "client_id", "virtualbiz-local",
                "redirect_uri", "http://localhost:3000/platform/callback", "scope", "openid email profile orgs",
                "state", "s", "nonce", "n", "code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                "code_challenge_method", "S256");
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/oidc/authorize/check")
                .header("Authorization", "Bearer " + u.token());
        q.forEach(req::queryParam);
        JsonNode d = json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(d.path("outcome").asText()).isEqualTo("CONSENT");
        assertThat(d.path("clientName").asText()).isEqualTo("VirtualBiz");
    }
}
