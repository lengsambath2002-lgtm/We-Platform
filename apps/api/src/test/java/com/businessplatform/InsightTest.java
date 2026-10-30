package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** The HR dashboard, the org chart and the HR reports. */
class InsightTest extends IntegrationTest {

    private String csv(User u, String path) throws Exception {
        var res = mvc.perform(MockMvcRequestBuilders.get(path).header("Authorization", "Bearer " + u.token())).andReturn().getResponse();
        assertThat(res.getStatus()).as(path).isEqualTo(200);
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void hr_sees_the_dashboard_and_reports_everyone_sees_the_org_chart() throws Exception {
        User owner = signUp("Ins Owner");
        User manager = signUp("Ins Manager");
        User employee = signUp("=Sneaky Formula");
        String org = newOrg(owner);
        join(owner, org, manager, "MEMBER");
        join(owner, org, employee, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        String hr = "/api/orgs/" + org + "/hr";
        ok(call(owner, HttpMethod.PUT, hr + "/people/" + employee.id(), new HashMap<>(Map.of("managerId", manager.id(), "department", "Sales"))));
        ok(call(owner, HttpMethod.PUT, hr + "/people/" + manager.id(), new HashMap<>(Map.of("department", "Sales", "title", "Sales lead"))));
        ok(call(employee, HttpMethod.POST, hr + "/attendance/clock-in", null));

        JsonNode d = ok(call(owner, HttpMethod.GET, hr + "/dashboard", null)).path("data");
        assertThat(d.path("headcount").asLong()).isEqualTo(3);
        assertThat(d.path("departments").get(0).path("label").asText()).isEqualTo("Sales");
        assertThat(d.path("departments").get(0).path("count").asLong()).isEqualTo(2);
        assertThat(d.path("today").path("present").asLong()).isEqualTo(1);
        assertThat(d.path("joinedLast90Days").asLong()).isEqualTo(3);
        assertThat(code(call(employee, HttpMethod.GET, hr + "/dashboard", null))).isEqualTo("FORBIDDEN");

        JsonNode chart = ok(call(employee, HttpMethod.GET, hr + "/org-chart", null)).path("data");
        assertThat(chart).hasSize(3);
        for (JsonNode n : chart) {
            if (n.path("person").path("id").asText().equals(employee.id().toString())) {
                assertThat(n.path("managerId").asText()).isEqualTo(manager.id().toString());
            }
        }

        String people = csv(owner, hr + "/reports/people.csv");
        assertThat(people).startsWith("﻿Name,Email").contains("Sales lead");
        // A name that looks like a formula is neutralised for spreadsheets.
        assertThat(people).contains("'=Sneaky Formula").doesNotContain("\n=Sneaky");
        String month = YearMonth.now(CalendarService.ZONE).toString();
        assertThat(csv(owner, hr + "/reports/attendance.csv?month=" + month)).contains("Ins Manager").contains(month);
        assertThat(csv(owner, hr + "/reports/leave.csv?year=2026")).startsWith("﻿Name,Department,Year");
        var refused = mvc.perform(MockMvcRequestBuilders.get(hr + "/reports/people.csv").header("Authorization", "Bearer " + employee.token()))
                .andReturn().getResponse();
        assertThat(refused.getStatus()).isEqualTo(403);
    }
}
