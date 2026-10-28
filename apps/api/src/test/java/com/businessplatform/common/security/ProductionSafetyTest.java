package com.businessplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Production must not start with the local secret, insecure cookies, an http app URL, or OIDC without a key. */
class ProductionSafetyTest {

    private static AuthProperties auth(String secret, boolean secure) {
        return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30), secure);
    }

    @Test
    void local_settings_are_fine_on_a_developer_machine() {
        assertThat(ProductionSafety.problems("local", auth(ProductionSafety.LOCAL_SECRET, false), "http://localhost:3300"))
                .isEmpty();
        assertThat(ProductionSafety.oidcProblems("local", true, false, List.of("http://localhost:3000/platform/callback")))
                .isEmpty();
    }

    @Test
    void production_refuses_local_settings() {
        assertThat(ProductionSafety.problems("production", auth(ProductionSafety.LOCAL_SECRET, false), "http://x"))
                .hasSize(3);
    }

    @Test
    void production_with_real_settings_starts() {
        assertThat(ProductionSafety.problems("production", auth("c2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLWhzMjU2LWtleXM=", true),
                "https://app.example.com")).isEmpty();
    }

    @Test
    void production_with_oidc_clients_needs_a_signing_key_and_https_redirects() {
        assertThat(ProductionSafety.oidcProblems("production", true, false, List.of("https://shop.example.com/platform/callback")))
                .singleElement().asString().contains("PLATFORM_OIDC_SIGNING_KEY");
        assertThat(ProductionSafety.oidcProblems("production", true, true, List.of("http://shop.example.com/platform/callback")))
                .singleElement().asString().contains("https");
        assertThat(ProductionSafety.oidcProblems("production", true, true, List.of("https://shop.example.com/platform/callback")))
                .isEmpty();
        assertThat(ProductionSafety.oidcProblems("production", false, false, List.of())).as("no clients, no OIDC").isEmpty();
    }
}
