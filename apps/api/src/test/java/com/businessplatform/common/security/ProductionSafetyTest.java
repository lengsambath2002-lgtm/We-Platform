package com.businessplatform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Production must not start with the local secret, insecure cookies or an http app URL. */
class ProductionSafetyTest {

    private static AuthProperties auth(String secret, boolean secure) {
        return new AuthProperties(secret, Duration.ofMinutes(15), Duration.ofDays(30), secure);
    }

    @Test
    void local_settings_are_fine_on_a_developer_machine() {
        assertThat(ProductionSafety.problems("local", auth(ProductionSafety.LOCAL_SECRET, false), "http://localhost:3300"))
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
}
