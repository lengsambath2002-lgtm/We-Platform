package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.support.IntegrationTest;

/** General Business and Service & Operations share one customer list: a customer added in either is in both. */
class SharedCustomersTest extends IntegrationTest {

    @Test
    void a_customer_added_in_business_is_a_service_customer_and_the_other_way_round() throws Exception {
        User owner = signUp("Shared Owner");
        String org = newOrg(owner);
        String biz = "/api/orgs/" + org + "/business";
        String svc = "/api/orgs/" + org + "/service";
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/business", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/service", Map.of("enabled", true)));

        String sold = ok(call(owner, HttpMethod.POST, biz + "/customers", Map.of("kind", "COMPANY", "name", "Angkor Clinic", "phone", "+855 12 000 111")))
                .path("data").path("id").asText();
        assertThat(ok(call(owner, HttpMethod.GET, svc + "/customers?q=Angkor", null)).path("data").path("items").toString()).contains(sold);
        assertThat(ok(call(owner, HttpMethod.GET, svc + "/customers/" + sold, null)).toString()).contains("Angkor Clinic");

        String served = ok(call(owner, HttpMethod.POST, svc + "/customers", Map.of("name", "Riverside Hotel", "phone", "+855 12 222 333")))
                .path("data").path("id").asText();
        assertThat(ok(call(owner, HttpMethod.GET, biz + "/customers/" + served, null)).toString()).contains("Riverside Hotel").contains("COMPANY");
    }
}
