package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import com.businessplatform.support.IntegrationTest;

/** Sign-up records the Terms / Privacy version accepted; the legal pages' addresses can't be taken by an organisation. */
class LegalTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sign_up_records_the_accepted_terms_version() throws Exception {
        User u = signUp("Legal Reader");
        Map<String, Object> row = jdbc.queryForMap("SELECT terms_version, terms_accepted_at FROM app_user WHERE id = ?", u.id());
        assertThat(row.get("terms_version")).isEqualTo("2026-11-02");
        assertThat(row.get("terms_accepted_at")).isNotNull();
        for (String slug : new String[] {"terms", "privacy"}) {
            assertThat(code(call(u, HttpMethod.POST, "/api/orgs", Map.of("name", "Taken " + slug, "slug", slug)))).isEqualTo("CONFLICT");
        }
    }
}
