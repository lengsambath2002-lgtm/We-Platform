package com.businessplatform.oidc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.businessplatform.common.security.Tokens;

/** Client and signing-key configuration is checked at start-up; nothing secret is echoed. */
class OidcConfigTest {

    private static final String HASH = Tokens.sha256("a-long-random-secret-for-the-test");

    private static OidcClients clients(String json, String env) {
        return new OidcClients(new OidcProperties(null, json, null), env);
    }

    @Test
    void clients_from_json_and_the_local_client_only_locally() {
        String json = "[{\"id\":\"virtualbiz\",\"name\":\"VirtualBiz\",\"secretHash\":\"" + HASH
                + "\",\"redirectUris\":[\"https://shop.example.com/platform/callback\"]}]";
        OidcClients prod = clients(json, "production");
        assertThat(prod.find("virtualbiz")).isPresent();
        assertThat(prod.find(OidcClients.LOCAL_ID)).isEmpty();
        assertThat(prod.authenticate("virtualbiz", "a-long-random-secret-for-the-test")).isPresent();
        assertThat(prod.authenticate("virtualbiz", "wrong")).isEmpty();
        assertThat(prod.anyConfigured()).isTrue();

        OidcClients local = clients(null, "local");
        assertThat(local.anyConfigured()).isFalse();
        assertThat(local.authenticate(OidcClients.LOCAL_ID, "local-dev-secret")).isPresent();
        assertThat(local.find(OidcClients.LOCAL_ID).orElseThrow().redirectUris())
                .containsExactly("http://localhost:3000/platform/callback", "http://localhost:3100/platform/callback");
        assertThat(clients(null, "staging").find(OidcClients.LOCAL_ID)).isEmpty();
    }

    @Test
    void bad_client_configuration_refuses_to_start() {
        assertThatThrownBy(() -> clients("not json", "local")).hasMessageContaining("PLATFORM_OIDC_CLIENTS");
        assertThatThrownBy(() -> clients("[{\"id\":\"x\",\"secretHash\":\"plain-secret\",\"redirectUris\":[\"https://a.b/c\"]}]",
                "local")).hasMessageContaining("SHA-256").hasMessageNotContaining("plain-secret");
        assertThatThrownBy(() -> clients("[{\"id\":\"x\",\"secretHash\":\"" + HASH + "\",\"redirectUris\":[\"https://a.b/c#f\"]}]",
                "local")).hasMessageContaining("redirect URI");
        assertThatThrownBy(() -> clients("[{\"id\":\"x\",\"secretHash\":\"" + HASH + "\",\"redirectUris\":[]}]", "local"))
                .hasMessageContaining("redirect URI");
        String one = "{\"id\":\"x\",\"secretHash\":\"" + HASH + "\",\"redirectUris\":[\"https://a.b/c\"]}";
        assertThatThrownBy(() -> clients("[" + one + "," + one + "]", "local")).hasMessageContaining("twice");
    }

    @Test
    void signing_key_from_env_or_generated_locally_only() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder().encodeToString(
                g.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n";
        String b64 = Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
        OidcClients none = clients(null, "production");
        assertThat(new OidcKeys(new OidcProperties(b64, null, List.of()), none, "production").available()).isTrue();
        assertThat(new OidcKeys(new OidcProperties(null, null, List.of()), clients(null, "local"), "local").available()).isTrue();
        assertThat(new OidcKeys(new OidcProperties(null, null, List.of()), none, "production").available()).isFalse();
        String json = "[{\"id\":\"virtualbiz\",\"secretHash\":\"" + HASH + "\",\"redirectUris\":[\"https://a.b/c\"]}]";
        assertThatThrownBy(() -> new OidcKeys(new OidcProperties(null, null, List.of()), clients(json, "staging"), "staging"))
                .hasMessageContaining("PLATFORM_OIDC_SIGNING_KEY");
        assertThatThrownBy(() -> new OidcKeys(new OidcProperties("bm90IGEga2V5", null, List.of()), none, "production"))
                .hasMessageContaining("PKCS#8").hasMessageNotContaining("bm90IGEga2V5");
    }
}
