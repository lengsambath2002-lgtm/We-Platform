package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.hr.service.LeaveDays;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** HR & Workforce: module switch, profiles and privacy, leave through the approval engine, balances, isolation. */
class HrTest extends IntegrationTest {

    /** Owner (HR manager), a manager and an employee who reports to them, with HR switched on. */
    private record Setup(User owner, User manager, User employee, String org) {

        String hr() {
            return "/api/orgs/" + org + "/hr";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Hr Owner");
        User manager = signUp("Team Manager");
        User employee = signUp("Dara Employee");
        String org = newOrg(owner);
        join(owner, org, manager, "MEMBER");
        join(owner, org, employee, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        Setup s = new Setup(owner, manager, employee, org);
        ok(call(owner, HttpMethod.PUT, s.hr() + "/people/" + employee.id(), profile(Map.of("managerId", manager.id(),
                "title", "Accountant", "department", "Finance"))));
        return s;
    }

    private static Map<String, Object> profile(Map<String, Object> fields) {
        return new HashMap<>(fields);
    }

    private String type(User u, Setup s, String name) throws Exception {
        for (JsonNode t : ok(call(u, HttpMethod.GET, s.hr() + "/leave-types", null)).path("data")) {
            if (t.path("nameEn").asText().equals(name)) {
                return t.path("id").asText();
            }
        }
        throw new AssertionError(name);
    }

    private JsonNode ask(User u, Setup s, String type, String start, String end, boolean halfStart, boolean halfEnd)
            throws Exception {
        return call(u, HttpMethod.POST, s.hr() + "/leave", Map.of("leaveTypeId", type(u, s, type), "startDate", start,
                "endDate", end, "halfStart", halfStart, "halfEnd", halfEnd, "reason", "Family visit"));
    }

    private JsonNode balance(User u, Setup s, String type, int year) throws Exception {
        for (JsonNode b : ok(call(u, HttpMethod.GET, s.hr() + "/balances?year=" + year, null)).path("data")) {
            if (b.path("type").path("nameEn").asText().equals(type)) {
                return b;
            }
        }
        throw new AssertionError(type);
    }

    private static double num(JsonNode n) {
        return n.decimalValue().doubleValue();
    }

    @Test
    void the_module_is_off_until_an_admin_switches_it_on() throws Exception {
        User owner = signUp("Off Owner");
        User member = signUp("Off Member");
        String org = newOrg(owner);
        join(owner, org, member, "MEMBER");
        String hr = "/api/orgs/" + org + "/hr";
        for (String path : List.of("/people", "/leave-types", "/balances", "/leave/mine")) {
            assertThat(code(call(owner, HttpMethod.GET, hr + path, null))).as(path).isEqualTo("MODULE_DISABLED");
        }
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null)).path("data")
                .path("permissions").toString()).doesNotContain("hr.");
        assertThat(code(call(member, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true))))
                .isEqualTo("FORBIDDEN");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        // Any first HR call works, even one that only reads (the defaults are seeded on the way).
        ok(call(member, HttpMethod.GET, hr + "/absences?from=2027-03-01&to=2027-03-07", null));
        List<String> ownerPerms = json.convertValue(ok(call(owner, HttpMethod.GET, "/api/orgs/" + org + "/context", null))
                .path("data").path("permissions"), List.class);
        List<String> memberPerms = json.convertValue(ok(call(member, HttpMethod.GET, "/api/orgs/" + org + "/context", null))
                .path("data").path("permissions"), List.class);
        assertThat(ownerPerms).contains("hr.view", "hr.manage");
        assertThat(memberPerms).contains("hr.view").doesNotContain("hr.manage");
        // The editable defaults appear on first use.
        assertThat(ok(call(member, HttpMethod.GET, hr + "/leave-types", null)).path("data").findValuesAsText("nameEn"))
                .containsExactly("Annual leave", "Sick leave", "Unpaid leave");

        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", false)));
        assertThat(code(call(member, HttpMethod.GET, hr + "/people", null))).isEqualTo("MODULE_DISABLED");
    }

    @Test
    void working_days_skip_weekends_and_count_half_days() {
        LocalDate fri = LocalDate.of(2027, 3, 5);
        LocalDate mon = LocalDate.of(2027, 3, 8);
        assertThat(LeaveDays.count(fri, mon, false, false)).isEqualByComparingTo("2");
        assertThat(LeaveDays.count(fri, mon, true, false)).isEqualByComparingTo("1.5");
        assertThat(LeaveDays.count(fri, mon, true, true)).isEqualByComparingTo("1");
        assertThat(LeaveDays.count(fri, fri, true, false)).isEqualByComparingTo("0.5");
        assertThat(LeaveDays.count(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 12), false, false)).isEqualByComparingTo("10");
        assertThat(LeaveDays.count(LocalDate.of(2027, 3, 6), LocalDate.of(2027, 3, 7), false, false)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void the_api_counts_days_and_refuses_bad_dates() throws Exception {
        Setup s = setup();
        JsonNode l = ok(ask(s.employee(), s, "Annual leave", "2027-03-05", "2027-03-08", true, false)).path("data");
        assertThat(num(l.path("days"))).isEqualTo(1.5);
        assertThat(l.path("status").asText()).isEqualTo("PENDING");
        assertThat(code(ask(s.employee(), s, "Annual leave", "2027-03-13", "2027-03-14", false, false))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(ask(s.employee(), s, "Annual leave", "2027-03-16", "2027-03-15", false, false))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(ask(s.employee(), s, "Annual leave", "2027-03-16", "2027-03-16", true, true))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(ask(s.employee(), s, "Annual leave", "2027-12-30", "2028-01-04", false, false))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void the_manager_approves_from_the_approvals_inbox_and_the_balance_is_used() throws Exception {
        Setup s = setup();
        JsonNode l = ok(ask(s.employee(), s, "Annual leave", "2027-03-01", "2027-03-03", false, false)).path("data");
        String leaveId = l.path("id").asText();
        assertThat(num(l.path("days"))).isEqualTo(3);
        JsonNode b = balance(s.employee(), s, "Annual leave", 2027);
        assertThat(num(b.path("pending"))).isEqualTo(3);
        assertThat(num(b.path("remaining"))).isEqualTo(15);

        // Step 1 is the manager (a MANAGER step resolved from the profile), not the Admins.
        JsonNode inbox = ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data");
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).path("recordType").asText()).isEqualTo("leave");
        assertThat(inbox.get(0).path("recordLink").asText()).isEqualTo("/" + s.org() + "/hr/leave/" + leaveId);
        assertThat(inbox.get(0).path("title").asText()).contains("Annual leave", "Dara Employee", "1 Mar 2027");
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data")).isEmpty();
        String approvalId = inbox.get(0).path("id").asText();
        JsonNode detail = ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals/" + approvalId, null)).path("data");
        assertThat(detail.path("workflowName").asText()).isEqualTo("Manager sign-off");
        assertThat(detail.path("steps").get(0).path("approver").path("id").asText()).isEqualTo(s.manager().id().toString());
        assertThat(detail.path("text").asText()).isEqualTo("Family visit");

        ok(call(s.manager(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approvalId + "/approve", Map.of()));
        JsonNode after = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/leave/" + leaveId, null)).path("data");
        assertThat(after.path("status").asText()).isEqualTo("APPROVED");
        assertThat(after.path("approvalStatus").asText()).isEqualTo("APPROVED");
        b = balance(s.employee(), s, "Annual leave", 2027);
        assertThat(num(b.path("used"))).isEqualTo(3);
        assertThat(num(b.path("pending"))).isEqualTo(0);
        assertThat(num(b.path("remaining"))).isEqualTo(15);

        // Approved leave is on everyone's calendar and in "who's away".
        JsonNode cal = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/calendar?from=2027-03-01&to=2027-03-31", null)).path("data");
        assertThat(cal.path("periods")).hasSize(1);
        assertThat(cal.path("periods").get(0).path("kind").asText()).isEqualTo("leave");
        assertThat(cal.path("periods").get(0).path("title").asText()).isEqualTo("Dara Employee");
        JsonNode away = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/absences?from=2027-03-02&to=2027-03-02", null)).path("data");
        assertThat(away).hasSize(1);
        assertThat(away.get(0).path("type").isNull()).isTrue();

        // The record's activity and the requester's notification follow.
        List<String> verbs = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null))
                .path("data").path("items").findValuesAsText("verb");
        assertThat(verbs).contains("leave.requested", "approval.requested", "approval.approved");
        assertThat(ok(call(s.employee(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data")
                .path("items").findValuesAsText("type")).contains("approval.approved");

        // Cancelling leave that hasn't started gives the days back.
        JsonNode cancelled = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/leave/" + leaveId + "/cancel",
                Map.of("reason", "Trip moved"))).path("data");
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(num(balance(s.employee(), s, "Annual leave", 2027).path("remaining"))).isEqualTo(18);
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null))
                .path("data").path("items").findValuesAsText("verb")).contains("leave.cancelled");
        assertThat(ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/notifications", null)).path("data")
                .path("items").findValuesAsText("type")).contains("leave.cancelled");
    }

    @Test
    void reject_and_withdraw_give_the_days_back() throws Exception {
        Setup s = setup();
        ok(ask(s.employee(), s, "Annual leave", "2027-04-05", "2027-04-09", false, false));
        String approvalId = ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null))
                .path("data").get(0).path("id").asText();
        assertThat(num(balance(s.employee(), s, "Annual leave", 2027).path("remaining"))).isEqualTo(13);
        ok(call(s.manager(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + approvalId + "/reject",
                Map.of("comment", "Month-end close")));
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.hr() + "/leave/mine", null)).path("data").get(0)
                .path("status").asText()).isEqualTo("REJECTED");
        assertThat(num(balance(s.employee(), s, "Annual leave", 2027).path("remaining"))).isEqualTo(18);

        // The same days can be asked again; withdrawing from HR goes through the engine.
        String again = ok(ask(s.employee(), s, "Annual leave", "2027-04-05", "2027-04-06", false, false)).path("data").path("id").asText();
        assertThat(code(call(s.manager(), HttpMethod.POST, s.hr() + "/leave/" + again + "/withdraw", null))).isEqualTo("FORBIDDEN");
        JsonNode w = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/leave/" + again + "/withdraw", null)).path("data");
        assertThat(w.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(w.path("approvalStatus").asText()).isEqualTo("WITHDRAWN");
        assertThat(num(balance(s.employee(), s, "Annual leave", 2027).path("remaining"))).isEqualTo(18);
        assertThat(ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data")).isEmpty();

        // Withdrawing from the Approvals page settles the leave too.
        ok(ask(s.employee(), s, "Sick leave", "2027-04-12", "2027-04-12", false, false));
        String sickApproval = ok(call(s.employee(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=sent", null))
                .path("data").get(0).path("id").asText();
        ok(call(s.employee(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + sickApproval + "/withdraw", null));
        assertThat(num(balance(s.employee(), s, "Sick leave", 2027).path("pending"))).isEqualTo(0);
    }

    @Test
    void overlapping_and_over_allowance_requests_are_refused_and_hr_can_adjust() throws Exception {
        Setup s = setup();
        ok(ask(s.employee(), s, "Annual leave", "2027-05-03", "2027-05-04", false, false));
        JsonNode overlap = ask(s.employee(), s, "Unpaid leave", "2027-05-04", "2027-05-07", false, false);
        assertThat(code(overlap)).isEqualTo("CONFLICT");
        // Sick leave: 7 days a year; 8 working days is too many, unpaid leave is unlimited.
        assertThat(code(ask(s.employee(), s, "Sick leave", "2027-06-01", "2027-06-10", false, false))).isEqualTo("VALIDATION_FAILED");
        assertThat(balance(s.employee(), s, "Unpaid leave", 2027).path("remaining").isNull()).isTrue();
        ok(ask(s.employee(), s, "Unpaid leave", "2027-06-01", "2027-06-30", false, false));

        // HR adds days with a reason (members can't); then the request fits.
        String sick = type(s.owner(), s, "Sick leave");
        Map<String, Object> adjust = Map.of("userId", s.employee().id(), "leaveTypeId", sick, "year", 2027, "days", 3,
                "reason", "Hospital stay agreed");
        assertThat(code(call(s.manager(), HttpMethod.POST, s.hr() + "/balances/adjust", adjust))).isEqualTo("FORBIDDEN");
        JsonNode after = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/balances/adjust", adjust)).path("data");
        assertThat(after.findValues("allowance").stream().map(JsonNode::asDouble).toList()).contains(10.0);
        assertThat(code(call(s.owner(), HttpMethod.POST, s.hr() + "/balances/adjust", Map.of("userId", s.employee().id(),
                "leaveTypeId", type(s.owner(), s, "Unpaid leave"), "year", 2027, "days", 2, "reason", "x"))))
                .isEqualTo("VALIDATION_FAILED");
        ok(ask(s.employee(), s, "Sick leave", "2027-07-01", "2027-07-12", false, false));
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.hr() + "/balances/" + s.employee().id() + "/adjustments?year=2027", null))
                .path("data").get(0).path("reason").asText()).isEqualTo("Hospital stay agreed");
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null))
                .path("data").path("items").findValuesAsText("verb")).contains("leave.balance.adjusted");

        // Leave types: HR managers add and edit them; half days can be turned off.
        String id = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/leave-types", Map.of("nameEn", "Maternity leave",
                "nameKm", "ច្បាប់សម្រាកលំហែមាតុភាព", "paid", true, "allowanceDays", 90, "halfDays", false))).path("data").path("id").asText();
        assertThat(code(call(s.manager(), HttpMethod.PUT, s.hr() + "/leave-types/" + id, Map.of("nameEn", "X", "nameKm", "X",
                "paid", true, "halfDays", true)))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.owner(), HttpMethod.POST, s.hr() + "/leave-types", Map.of("nameEn", "annual LEAVE",
                "nameKm", "x", "paid", true, "halfDays", true)))).isEqualTo("CONFLICT");
        assertThat(code(ask(s.employee(), s, "Maternity leave", "2027-08-02", "2027-08-03", true, false))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void without_a_manager_the_admins_decide() throws Exception {
        Setup s = setup();
        // The manager has no manager of their own: the step goes to the Admins (the owner here).
        ok(ask(s.manager(), s, "Annual leave", "2027-03-01", "2027-03-01", false, false));
        JsonNode inbox = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data");
        assertThat(inbox).hasSize(1);
        String id = inbox.get(0).path("id").asText();
        JsonNode detail = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals/" + id, null)).path("data");
        assertThat(detail.path("steps").get(0).path("role").asText()).isEqualTo("ADMIN");
        assertThat(code(call(s.employee(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of())))
                .isEqualTo("FORBIDDEN");
        ok(call(s.owner(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + id + "/approve", Map.of()));
        assertThat(num(balance(s.manager(), s, "Annual leave", 2027).path("used"))).isEqualTo(1);

        // A manager who leaves the organisation stops counting: the Admins decide again.
        String membership = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/members", null)).path("data")
                .findParents("userId").stream().filter(m -> m.path("userId").asText().equals(s.manager().id().toString()))
                .findFirst().orElseThrow().path("membershipId").asText();
        ok(call(s.owner(), HttpMethod.PATCH, "/api/orgs/" + s.org() + "/members/" + membership, Map.of("status", "SUSPENDED")));
        ok(ask(s.employee(), s, "Annual leave", "2027-03-02", "2027-03-02", false, false));
        assertThat(ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals?box=inbox", null)).path("data")).hasSize(1);
    }

    @Test
    void members_see_colleagues_without_private_fields_and_edit_only_their_own_contact() throws Exception {
        Setup s = setup();
        ok(call(s.owner(), HttpMethod.PUT, s.hr() + "/people/" + s.employee().id(), profile(Map.of("managerId", s.manager().id(),
                "title", "Accountant", "department", "Finance", "employmentType", "FULL_TIME", "startDate", "2024-01-15",
                "emergencyName", "Sok Mother", "emergencyPhone", "+855 12 345 678", "workPhone", "+855 23 111 222"))));

        JsonNode directory = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/people", null)).path("data");
        assertThat(directory).hasSize(3);
        assertThat(directory.toString()).contains("Finance", "Accountant").doesNotContain("Sok Mother", "FULL_TIME");
        JsonNode other = ok(call(s.manager(), HttpMethod.GET, s.hr() + "/people/" + s.employee().id(), null)).path("data");
        assertThat(other.path("privateShown").asBoolean()).isFalse();
        assertThat(other.path("emergencyName").isNull()).isTrue();
        assertThat(other.path("employmentType").isNull()).isTrue();
        assertThat(other.path("manager").path("id").asText()).isEqualTo(s.manager().id().toString());
        JsonNode me = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/people/" + s.employee().id(), null)).path("data");
        assertThat(me.path("emergencyName").asText()).isEqualTo("Sok Mother");
        assertThat(me.path("canEdit").asBoolean()).isFalse();
        assertThat(me.path("canEditContact").asBoolean()).isTrue();
        assertThat(ok(call(s.manager(), HttpMethod.GET, s.hr() + "/people/" + s.manager().id(), null)).path("data")
                .path("reports").findValuesAsText("name")).containsExactly("Dara Employee");

        // Only HR edits someone else, or job details; people edit their own phone and emergency contact.
        assertThat(code(call(s.manager(), HttpMethod.PUT, s.hr() + "/people/" + s.employee().id(),
                profile(Map.of("title", "Boss"))))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.employee(), HttpMethod.PUT, s.hr() + "/people/" + s.employee().id(),
                profile(Map.of("title", "Boss"))))).isEqualTo("FORBIDDEN");
        ok(call(s.employee(), HttpMethod.PUT, s.hr() + "/me/contact", Map.of("workPhone", "012 999 888", "emergencyName", "Sok Father")));
        assertThat(code(call(s.employee(), HttpMethod.PUT, s.hr() + "/me/contact", Map.of("workPhone", "call me")))).isEqualTo("VALIDATION_FAILED");
        // No reporting loops, nobody manages themselves.
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.hr() + "/people/" + s.manager().id(),
                profile(Map.of("managerId", s.employee().id()))))).isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.hr() + "/people/" + s.manager().id(),
                profile(Map.of("managerId", s.manager().id()))))).isEqualTo("VALIDATION_FAILED");

        // Someone else's leave shows without its reason; everyone's requests and balances are for HR.
        String leaveId = ok(ask(s.employee(), s, "Annual leave", "2027-03-01", "2027-03-01", false, false)).path("data").path("id").asText();
        User colleague = signUp("Other Colleague");
        join(s.owner(), s.org(), colleague, "MEMBER");
        assertThat(ok(call(colleague, HttpMethod.GET, s.hr() + "/leave/" + leaveId, null)).path("data").path("reason").isNull()).isTrue();
        assertThat(ok(call(s.manager(), HttpMethod.GET, s.hr() + "/leave/" + leaveId, null)).path("data").path("reason").asText())
                .isEqualTo("Family visit");
        assertThat(code(call(colleague, HttpMethod.GET, s.hr() + "/leave", null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(colleague, HttpMethod.GET, s.hr() + "/balances?userId=" + s.employee().id(), null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(colleague, HttpMethod.POST, s.hr() + "/leave/" + leaveId + "/cancel", Map.of()))).isEqualTo("FORBIDDEN");
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.hr() + "/leave?status=PENDING", null)).path("data")).hasSize(1);
    }

    @Test
    void other_organisations_and_guests_get_nothing() throws Exception {
        Setup s = setup();
        String leaveId = ok(ask(s.employee(), s, "Annual leave", "2027-03-01", "2027-03-01", false, false)).path("data").path("id").asText();
        User outsider = signUp("Outsider");
        String theirs = newOrg(outsider);
        ok(call(outsider, HttpMethod.PUT, "/api/orgs/" + theirs + "/modules/hr", Map.of("enabled", true)));
        assertThat(code(call(outsider, HttpMethod.GET, s.hr() + "/people", null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(outsider, HttpMethod.GET, s.hr() + "/leave/" + leaveId, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(outsider, HttpMethod.GET, "/api/orgs/" + theirs + "/hr/leave/" + leaveId, null))).isEqualTo("NOT_FOUND");
        assertThat(code(call(outsider, HttpMethod.GET, "/api/orgs/" + theirs + "/hr/people/" + s.employee().id(), null)))
                .isEqualTo("NOT_FOUND");
        assertThat(ok(call(outsider, HttpMethod.GET, "/api/orgs/" + theirs + "/hr/people", null)).path("data")).hasSize(1);

        User guest = signUp("Guest Person");
        join(s.owner(), s.org(), guest, "GUEST");
        for (String path : List.of("/people", "/leave-types", "/balances", "/leave/mine", "/leave/" + leaveId,
                "/absences?from=2027-03-01&to=2027-03-31")) {
            assertThat(code(call(guest, HttpMethod.GET, s.hr() + path, null))).as(path).isEqualTo("FORBIDDEN");
        }
        assertThat(code(call(guest, HttpMethod.POST, s.hr() + "/leave", Map.of("leaveTypeId", type(s.owner(), s, "Annual leave"),
                "startDate", "2027-03-01", "endDate", "2027-03-01")))).isEqualTo("FORBIDDEN");
        List<String> perms = json.convertValue(ok(call(guest, HttpMethod.GET, "/api/orgs/" + s.org() + "/context", null))
                .path("data").path("permissions"), List.class);
        assertThat(perms).doesNotContain("hr.view", "hr.manage");
        // Guests are not in the directory either.
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.hr() + "/people", null)).path("data")).hasSize(3);
    }
}
