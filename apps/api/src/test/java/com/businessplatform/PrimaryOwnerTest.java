package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** One primary owner per organisation: only they delete it or hand the role over; they stay an owner until then. */
class PrimaryOwnerTest extends IntegrationTest {

    private List<String> permissions(User u, String org) throws Exception {
        List<String> out = new ArrayList<>();
        ok(call(u, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data").path("permissions").forEach(p -> out.add(p.asText()));
        return out;
    }

    private String membershipOf(User admin, String org, User who) throws Exception {
        for (JsonNode m : ok(call(admin, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(who.id().toString())) {
                return m.path("membershipId").asText();
            }
        }
        throw new AssertionError();
    }

    @Test
    void only_the_primary_owner_deletes_or_hands_over() throws Exception {
        User founder = signUp("Prim Founder");
        User partner = signUp("Prim Partner");
        String org = newOrg(founder);
        join(founder, org, partner, "ADMIN");
        String partnerM = membershipOf(founder, org, partner);
        ok(call(founder, HttpMethod.PATCH, "/api/orgs/" + org + "/members/" + partnerM, Map.of("role", "OWNER")));

        // Another owner may do everything except deleting and handing over.
        assertThat(permissions(founder, org)).contains("org.delete", "org.transfer");
        assertThat(permissions(partner, org)).doesNotContain("org.delete", "org.transfer");
        assertThat(code(call(partner, HttpMethod.DELETE, "/api/orgs/" + org, Map.of("name", "Org " + org)))).isEqualTo("FORBIDDEN");
        // The primary owner can't be demoted or suspended, nor leave, until they hand over.
        String founderM = membershipOf(founder, org, founder);
        assertThat(code(call(partner, HttpMethod.PATCH, "/api/orgs/" + org + "/members/" + founderM, Map.of("role", "ADMIN")))).isEqualTo("INVALID_STATE");
        assertThat(code(call(founder, HttpMethod.POST, "/api/orgs/" + org + "/leave", null))).isEqualTo("INVALID_STATE");
        assertThat(code(call(partner, HttpMethod.POST, "/api/orgs/" + org + "/transfer-ownership", Map.of("membershipId", partnerM))))
                .isEqualTo("FORBIDDEN");
        JsonNode members = ok(call(founder, HttpMethod.GET, "/api/orgs/" + org + "/members", null)).path("data");
        for (JsonNode m : members) {
            assertThat(m.path("primaryOwner").asBoolean()).isEqualTo(m.path("userId").asText().equals(founder.id().toString()));
        }

        // Handed over: the partner is now the primary owner; the founder may leave.
        assertThat(ok(call(founder, HttpMethod.POST, "/api/orgs/" + org + "/transfer-ownership", Map.of("membershipId", partnerM)))
                .path("data").path("primaryOwner").asBoolean()).isTrue();
        assertThat(permissions(partner, org)).contains("org.delete");
        assertThat(permissions(founder, org)).doesNotContain("org.delete");
        ok(call(founder, HttpMethod.POST, "/api/orgs/" + org + "/leave", null));
        ok(call(partner, HttpMethod.DELETE, "/api/orgs/" + org, Map.of("name", "Org " + org)));
    }
}
