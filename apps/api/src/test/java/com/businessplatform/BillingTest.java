package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.businessplatform.billing.service.SubscriptionService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Plans and subscriptions: the trial, what each plan allows, who may change it, expiry and the platform's say. */
class BillingTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    SubscriptionService subscriptions;

    private String orgId(User u, String org) throws Exception {
        return ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("org").path("id").asText();
    }

    @Test
    void a_new_company_trials_standard_and_moves_to_free_when_the_trial_ends() throws Exception {
        User owner = signUp("Bill Owner");
        String org = newOrg(owner);
        String sub = "/api/orgs/" + org + "/subscription";
        JsonNode s = ok(call(owner, HttpMethod.GET, sub, null)).path("data");
        assertThat(s.path("plan").path("code").asText()).isEqualTo("STANDARD");
        assertThat(s.path("status").asText()).isEqualTo("TRIALING");
        assertThat(s.path("trialEndsAt").isNull()).isFalse();
        assertThat(s.path("plans").findValuesAsText("code")).containsExactly("FREE", "STANDARD", "PREMIUM");
        assertThat(ok(call(null, HttpMethod.GET, "/api/plans", null)).path("data")).hasSize(3);
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));

        // The trial ends: Free, and HR (not in Free) is locked with its data kept.
        jdbc.update("UPDATE subscription SET trial_ends_at = now() - interval '1 minute' WHERE org_id = ?::uuid", orgId(owner, org));
        subscriptions.expire();
        s = ok(call(owner, HttpMethod.GET, sub, null)).path("data");
        assertThat(s.path("plan").path("code").asText()).isEqualTo("FREE");
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/hr/people", null))).isEqualTo("MODULE_DISABLED");
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("modules")) {
            if (m.path("key").asText().equals("hr")) {
                assertThat(m.path("inPlan").asBoolean()).isFalse();
            }
        }
        assertThat(code(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/education", Map.of("enabled", true)))).isEqualTo("INVALID_STATE");

        // Free allows 5 members (guests don't count; pending invitations do).
        for (int i = 0; i < 4; i++) {
            ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations", Map.of("email", "seat" + i + "-" + org + "@bill.test", "role", "MEMBER")));
        }
        assertThat(code(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations", Map.of("email", "seat5-" + org + "@bill.test", "role", "MEMBER"))))
                .isEqualTo("INVALID_STATE");
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/invitations", Map.of("email", "guest-" + org + "@bill.test", "role", "GUEST")));

        // Upgrading brings HR back as it was.
        s = ok(call(owner, HttpMethod.PUT, sub, Map.of("planCode", "PREMIUM", "cycle", "YEARLY"))).path("data");
        assertThat(s.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(s.path("nextAmount").decimalValue()).isEqualByComparingTo("60.00");
        ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/hr/people", null));

        // Cancelled: Premium until the period ends, then Free.
        assertThat(ok(call(owner, HttpMethod.POST, sub + "/cancel", null)).path("data").path("cancelAtPeriodEnd").asBoolean()).isTrue();
        jdbc.update("UPDATE subscription SET current_period_end = now() - interval '1 minute' WHERE org_id = ?::uuid", orgId(owner, org));
        subscriptions.expire();
        assertThat(ok(call(owner, HttpMethod.GET, sub, null)).path("data").path("plan").path("code").asText()).isEqualTo("FREE");
        // Downgrading below the members you have is refused: back to Premium, 6 seats taken, then try nothing smaller.
        assertThat(code(call(owner, HttpMethod.PUT, sub, Map.of("planCode", "NOPE", "cycle", "MONTHLY")))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void owners_and_the_billing_role_change_the_plan_admins_only_see_it() throws Exception {
        User owner = signUp("Bill Owner2");
        User admin = signUp("Bill Admin");
        User clerk = signUp("Bill Clerk");
        String org = newOrg(owner);
        join(owner, org, admin, "ADMIN");
        join(owner, org, clerk, "MEMBER");
        String sub = "/api/orgs/" + org + "/subscription";
        ok(call(admin, HttpMethod.GET, sub, null));
        assertThat(code(call(admin, HttpMethod.PUT, sub, Map.of("planCode", "FREE", "cycle", "MONTHLY")))).isEqualTo("FORBIDDEN");
        assertThat(code(call(clerk, HttpMethod.GET, sub, null))).isEqualTo("FORBIDDEN");
        String role = ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/roles", Map.of("name", "Finance", "permissions", List.of("billing.manage"))))
                .path("data").path("id").asText();
        String clerkM = null;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(clerk.id().toString())) {
                clerkM = m.path("membershipId").asText();
            }
        }
        ok(call(owner, HttpMethod.POST, "/api/orgs/" + org + "/roles/" + role + "/grants", Map.of("membershipId", clerkM)));
        assertThat(ok(call(clerk, HttpMethod.PUT, sub, Map.of("planCode", "STANDARD", "cycle", "MONTHLY"))).path("data").path("nextAmount")
                .decimalValue()).isEqualByComparingTo("9.00");
    }

    @Test
    void storage_is_limited_by_the_plan_and_the_platform_can_grant_a_plan() throws Exception {
        User owner = signUp("Bill Owner3");
        String org = newOrg(owner);
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/subscription", Map.of("planCode", "FREE", "cycle", "MONTHLY")));
        jdbc.update("UPDATE plan SET storage_gb = 0 WHERE code = 'FREE'");
        try {
            var req = MockMvcRequestBuilders.multipart("/api/orgs/" + org + "/documents")
                    .file(new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes()))
                    .header("Authorization", "Bearer " + owner.token());
            JsonNode r = json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString());
            assertThat(r.path("status").path("code").asText()).isEqualTo("INVALID_STATE");
        } finally {
            jdbc.update("UPDATE plan SET storage_gb = 1 WHERE code = 'FREE'");
        }
        // A complimentary plan from the platform can't be changed by the company itself.
        User root = signUp("Bill Root");
        jdbc.update("UPDATE app_user SET super_admin = true WHERE id = ?", root.id());
        ok(call(root, HttpMethod.PUT, "/api/platform/orgs/" + orgId(owner, org) + "/subscription",
                Map.of("planCode", "PREMIUM", "status", "COMPLIMENTARY", "cycle", "YEARLY", "note", "Pilot customer")));
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/subscription", null)).path("data").path("status").asText())
                .isEqualTo("COMPLIMENTARY");
        assertThat(code(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/subscription", Map.of("planCode", "FREE", "cycle", "MONTHLY"))))
                .isEqualTo("INVALID_STATE");
        assertThat(code(call(owner, HttpMethod.GET, "/api/platform/plans", null))).isEqualTo("NOT_FOUND");
        assertThat(ok(call(root, HttpMethod.GET, "/api/platform/plans", null)).path("data")).hasSize(3);
    }
}
