package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.mail.MailMessage;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** Notifications go by email too (English then Khmer), unless the person switched email off for that kind. */
class NotificationEmailTest extends IntegrationTest {

    @Test
    void an_approval_request_is_emailed_until_email_is_switched_off() throws Exception {
        User owner = signUp("Mail Owner");
        User member = signUp("Mail Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String api = "/api/orgs/" + org;
        ok(call(owner, HttpMethod.PUT, api + "/modules/hr", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, api + "/hr/people/" + member.id(), new HashMap<>(Map.of("managerId", owner.id()))));
        String type = ok(call(owner, HttpMethod.GET, api + "/hr/leave-types", null)).path("data").get(0).path("id").asText();
        ok(call(member, HttpMethod.POST, api + "/hr/leave", Map.of("leaveTypeId", type, "startDate", "2026-12-15", "endDate", "2026-12-15",
                "halfStart", false, "halfEnd", false)));

        MailMessage m = mails.await(owner.email(), 1);
        assertThat(m.subject()).startsWith("Approval requested");
        assertThat(m.text()).contains("Open in Business Platform").contains("———").contains("/hr/leave/");
        assertThat(m.html()).contains("Mail Member");

        // Email off for approval requests: the in-app notification still comes, no more email.
        JsonNode prefs = ok(call(owner, HttpMethod.PUT, api + "/notifications/preferences",
                Map.of("muted", List.of(), "emailOff", List.of("approval.requested")))).path("data").path("kinds");
        for (JsonNode k : prefs) {
            assertThat(k.path("email").asBoolean()).isEqualTo(!k.path("kind").asText().equals("approval.requested"));
        }
        ok(call(member, HttpMethod.POST, api + "/hr/leave", Map.of("leaveTypeId", type, "startDate", "2026-12-16", "endDate", "2026-12-16",
                "halfStart", false, "halfEnd", false)));
        Thread.sleep(500);
        assertThat(mails.to(owner.email())).hasSize(1);
        assertThat(ok(call(owner, HttpMethod.GET, api + "/notifications", null)).path("data").path("items")).hasSizeGreaterThanOrEqualTo(2);
    }
}
