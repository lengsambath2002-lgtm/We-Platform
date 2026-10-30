package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/** HR attendance: schedules, clocking in and out, timesheets and the day board, HR corrections, overtime. */
class AttendanceTest extends IntegrationTest {

    private record Setup(User owner, User manager, User employee, String org) {

        String hr() {
            return "/api/orgs/" + org + "/hr";
        }
    }

    private Setup setup() throws Exception {
        User owner = signUp("Att Owner");
        User manager = signUp("Att Manager");
        User employee = signUp("Att Employee");
        String org = newOrg(owner);
        join(owner, org, manager, "MEMBER");
        join(owner, org, employee, "MEMBER");
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + org + "/modules/hr", Map.of("enabled", true)));
        Setup s = new Setup(owner, manager, employee, org);
        // Started a while ago, so past working days count.
        Map<String, Object> profile = new HashMap<>(Map.of("managerId", manager.id(),
                "startDate", LocalDate.now(CalendarService.ZONE).minusYears(1).toString()));
        ok(call(owner, HttpMethod.PUT, s.hr() + "/people/" + employee.id(), profile));
        return s;
    }

    /** The latest weekdays before today, newest first. */
    private static List<LocalDate> pastWeekdays() {
        LocalDate d = LocalDate.now(CalendarService.ZONE).minusDays(1);
        java.util.ArrayList<LocalDate> out = new java.util.ArrayList<>();
        while (out.size() < 2) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                out.add(d);
            }
            d = d.minusDays(1);
        }
        return out;
    }

    private JsonNode day(User u, Setup s, String userId, LocalDate date) throws Exception {
        JsonNode sheet = ok(call(u, HttpMethod.GET, s.hr() + "/attendance/timesheet?userId=" + userId + "&month="
                + date.toString().substring(0, 7), null)).path("data");
        for (JsonNode d : sheet.path("days")) {
            if (d.path("date").asText().equals(date.toString())) {
                return d;
            }
        }
        throw new AssertionError(date);
    }

    @Test
    void an_organisation_starts_with_office_hours_and_hr_sets_the_default() throws Exception {
        Setup s = setup();
        JsonNode list = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/schedules", null)).path("data");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).path("name").asText()).isEqualTo("Office hours");
        assertThat(list.get(0).path("isDefault").asBoolean()).isTrue();
        assertThat(list.get(0).path("dayMinutes").asInt()).isEqualTo(480);
        String office = list.get(0).path("id").asText();

        Map<String, Object> shift = Map.of("name", "Early shift", "startTime", "06:00", "endTime", "14:00",
                "breakMinutes", 30, "graceMinutes", 5, "workDays", List.of(1, 2, 3, 4, 5, 6), "makeDefault", true);
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/schedules", shift))).isEqualTo("FORBIDDEN");
        JsonNode early = ok(call(s.owner(), HttpMethod.POST, s.hr() + "/schedules", shift)).path("data");
        assertThat(early.path("isDefault").asBoolean()).isTrue();
        assertThat(code(call(s.owner(), HttpMethod.POST, s.hr() + "/schedules", shift))).isEqualTo("CONFLICT");
        assertThat(code(call(s.owner(), HttpMethod.DELETE, s.hr() + "/schedules/" + early.path("id").asText(), null)))
                .isEqualTo("INVALID_STATE");

        // Someone gets their own; removing it puts them back on the default.
        ok(call(s.owner(), HttpMethod.PUT, s.hr() + "/people/" + s.employee().id() + "/schedule", Map.of("scheduleId", office)));
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.hr() + "/attendance/today", null)).path("data")
                .path("schedule").path("name").asText()).isEqualTo("Office hours");
        ok(call(s.owner(), HttpMethod.DELETE, s.hr() + "/schedules/" + office, null));
        assertThat(ok(call(s.employee(), HttpMethod.GET, s.hr() + "/attendance/today", null)).path("data")
                .path("schedule").path("name").asText()).isEqualTo("Early shift");
    }

    @Test
    void someone_clocks_in_and_out_once_a_day() throws Exception {
        Setup s = setup();
        JsonNode today = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/attendance/today", null)).path("data");
        assertThat(today.path("canClockIn").asBoolean()).isTrue();
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/attendance/clock-out", null))).isEqualTo("INVALID_STATE");

        JsonNode in = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/attendance/clock-in", Map.of("note", "From the office"))).path("data");
        assertThat(in.path("canClockIn").asBoolean()).isFalse();
        assertThat(in.path("canClockOut").asBoolean()).isTrue();
        assertThat(in.path("day").path("status").asText()).isIn("PRESENT", "LATE");
        assertThat(in.path("day").path("note").asText()).isEqualTo("From the office");
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/attendance/clock-in", null))).isEqualTo("INVALID_STATE");

        JsonNode out = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/attendance/clock-out", null)).path("data");
        assertThat(out.path("canClockOut").asBoolean()).isFalse();
        assertThat(out.path("day").path("clockOut").isNull()).isFalse();
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/attendance/clock-out", null))).isEqualTo("INVALID_STATE");

        // HR's board shows them at work today.
        JsonNode board = ok(call(s.owner(), HttpMethod.GET, s.hr() + "/attendance/board", null)).path("data");
        assertThat(board).hasSize(3);
        for (JsonNode row : board) {
            if (row.path("person").path("id").asText().equals(s.employee().id().toString())) {
                assertThat(row.path("day").path("status").asText()).isIn("PRESENT", "LATE");
            }
        }
        assertThat(code(call(s.employee(), HttpMethod.GET, s.hr() + "/attendance/board", null))).isEqualTo("FORBIDDEN");
    }

    @Test
    void past_days_show_late_and_absent_and_only_hr_corrects_them_with_a_reason() throws Exception {
        Setup s = setup();
        List<LocalDate> past = pastWeekdays();
        LocalDate late = past.get(0);
        LocalDate missed = past.get(1);
        String put = s.hr() + "/attendance/" + s.employee().id() + "/" + late;
        Map<String, Object> edit = Map.of("clockIn", "08:30", "clockOut", "17:30", "reason", "Forgot to clock in");
        assertThat(code(call(s.manager(), HttpMethod.PUT, put, edit))).isEqualTo("FORBIDDEN");
        assertThat(code(call(s.owner(), HttpMethod.PUT, put, Map.of("clockIn", "08:30", "reason", " "))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.owner(), HttpMethod.PUT, put, Map.of("clockIn", "09:00", "clockOut", "08:00", "reason", "x"))))
                .isEqualTo("VALIDATION_FAILED");
        assertThat(code(call(s.owner(), HttpMethod.PUT, s.hr() + "/attendance/" + s.employee().id() + "/"
                + LocalDate.now(CalendarService.ZONE).plusDays(2), edit))).isEqualTo("VALIDATION_FAILED");
        ok(call(s.owner(), HttpMethod.PUT, put, edit));

        // Their manager sees their timesheet; 08:30 on an 08:00 start (10 minutes' grace) is 30 minutes late.
        JsonNode d = day(s.manager(), s, s.employee().id().toString(), late);
        assertThat(d.path("status").asText()).isEqualTo("LATE");
        assertThat(d.path("lateMinutes").asInt()).isEqualTo(30);
        assertThat(d.path("workedMinutes").asInt()).isEqualTo(480);
        assertThat(d.path("corrected").asBoolean()).isTrue();
        assertThat(d.path("editReason").asText()).isEqualTo("Forgot to clock in");
        assertThat(day(s.employee(), s, s.employee().id().toString(), missed).path("status").asText()).isEqualTo("ABSENT");

        // Nobody else sees it.
        assertThat(code(call(s.owner(), HttpMethod.GET, s.hr() + "/attendance/timesheet?userId=" + s.manager().id(), null)))
                .isEqualTo("SUCCESS");
        assertThat(code(call(s.manager(), HttpMethod.GET, s.hr() + "/attendance/timesheet?userId=" + s.owner().id(), null)))
                .isEqualTo("FORBIDDEN");

        // Removing the record makes it a missed day again; both changes are in the audit log.
        ok(call(s.owner(), HttpMethod.PUT, put, Map.of("reason", "Was on a site visit, not in the office")));
        assertThat(day(s.owner(), s, s.employee().id().toString(), late).path("status").asText()).isEqualTo("ABSENT");
        JsonNode log = ok(call(s.owner(), HttpMethod.GET, "/api/orgs/" + s.org() + "/activity?module=hr", null)).path("data").path("items");
        assertThat(log.findValuesAsText("verb")).contains("attendance.corrected", "attendance.removed");
    }

    @Test
    void overtime_goes_to_the_manager_and_shows_on_the_timesheet_once_approved() throws Exception {
        Setup s = setup();
        LocalDate day = pastWeekdays().get(0);
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/overtime", Map.of("workDate",
                LocalDate.now(CalendarService.ZONE).plusDays(1).toString(), "minutes", 60, "reason", "Stock count"))))
                .isEqualTo("VALIDATION_FAILED");
        JsonNode o = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/overtime",
                Map.of("workDate", day.toString(), "minutes", 150, "reason", "Month-end closing"))).path("data");
        assertThat(o.path("status").asText()).isEqualTo("PENDING");
        assertThat(code(call(s.employee(), HttpMethod.POST, s.hr() + "/overtime",
                Map.of("workDate", day.toString(), "minutes", 30, "reason", "Again")))).isEqualTo("CONFLICT");

        JsonNode detail = ok(call(s.manager(), HttpMethod.GET, "/api/orgs/" + s.org() + "/approvals/" + o.path("approvalId").asText(), null))
                .path("data");
        assertThat(detail.path("steps").get(0).path("approver").path("id").asText()).isEqualTo(s.manager().id().toString());
        ok(call(s.manager(), HttpMethod.POST, "/api/orgs/" + s.org() + "/approvals/" + o.path("approvalId").asText() + "/approve", Map.of()));

        JsonNode mine = ok(call(s.employee(), HttpMethod.GET, s.hr() + "/overtime/mine", null)).path("data");
        assertThat(mine.get(0).path("status").asText()).isEqualTo("APPROVED");
        assertThat(day(s.employee(), s, s.employee().id().toString(), day).path("overtimeMinutes").asInt()).isEqualTo(150);
        assertThat(ok(call(s.owner(), HttpMethod.GET, s.hr() + "/overtime?status=APPROVED", null)).path("data")).hasSize(1);
        assertThat(code(call(s.employee(), HttpMethod.GET, s.hr() + "/overtime", null))).isEqualTo("FORBIDDEN");
    }

    @Test
    void a_waiting_overtime_request_can_be_withdrawn() throws Exception {
        Setup s = setup();
        JsonNode o = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/overtime",
                Map.of("workDate", pastWeekdays().get(1).toString(), "minutes", 45, "reason", "Client call"))).path("data");
        assertThat(code(call(s.manager(), HttpMethod.POST, s.hr() + "/overtime/" + o.path("id").asText() + "/withdraw", null)))
                .isEqualTo("FORBIDDEN");
        JsonNode w = ok(call(s.employee(), HttpMethod.POST, s.hr() + "/overtime/" + o.path("id").asText() + "/withdraw", null)).path("data");
        assertThat(w.path("status").asText()).isEqualTo("WITHDRAWN");
        assertThat(w.path("approvalStatus").asText()).isEqualTo("WITHDRAWN");
    }
}
