package com.businessplatform.hr.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.hr.payload.AttendancePayloads.*;
import com.businessplatform.hr.service.TimekeepingService;
import com.businessplatform.hr.service.OvertimeService;
import com.businessplatform.hr.service.ScheduleService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: work schedules, clocking in and out, timesheets, the day board and overtime. */
@RestController
@RequestMapping("/api/orgs/{org}/hr")
@RequiredArgsConstructor
@Tag(name = "HR attendance")
public class AttendanceController {

    private final ScheduleService schedules;
    private final TimekeepingService attendance;
    private final OvertimeService overtime;

    // ── Schedules ────────────────────────────────────────────────────────────────────

    @GetMapping("/schedules")
    public ResponseEntity<ApiResponse<List<ScheduleView>>> schedules(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(schedules.list(org)));
    }

    @PostMapping("/schedules")
    public ResponseEntity<ApiResponse<ScheduleView>> createSchedule(@PathVariable String org,
            @Valid @RequestBody ScheduleRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(schedules.save(org, null, in)));
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<ScheduleView>> updateSchedule(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ScheduleRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(schedules.save(org, id, in)));
    }

    @DeleteMapping("/schedules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(@PathVariable String org, @PathVariable UUID id) {
        schedules.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PutMapping("/people/{userId}/schedule")
    public ResponseEntity<ApiResponse<ScheduleView>> assignSchedule(@PathVariable String org, @PathVariable UUID userId,
            @RequestBody AssignScheduleRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(schedules.assign(org, userId, in)));
    }

    // ── Attendance ───────────────────────────────────────────────────────────────────

    @GetMapping("/attendance/today")
    public ResponseEntity<ApiResponse<Today>> today(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.today(org)));
    }

    @PostMapping("/attendance/clock-in")
    public ResponseEntity<ApiResponse<Today>> clockIn(@PathVariable String org,
            @Valid @RequestBody(required = false) ClockRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.clockIn(org, in)));
    }

    @PostMapping("/attendance/clock-out")
    public ResponseEntity<ApiResponse<Today>> clockOut(@PathVariable String org,
            @Valid @RequestBody(required = false) ClockRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.clockOut(org, in)));
    }

    /** HR managers: everyone's day ({@code date} defaults to today). */
    @GetMapping("/attendance/board")
    public ResponseEntity<ApiResponse<List<BoardRow>>> board(@PathVariable String org,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.board(org, date)));
    }

    /** {@code month}: yyyy-MM (default this month); {@code userId}: someone else (their manager or HR). */
    @GetMapping("/attendance/timesheet")
    public ResponseEntity<ApiResponse<Timesheet>> timesheet(@PathVariable String org,
            @RequestParam(required = false) UUID userId, @RequestParam(required = false) YearMonth month) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.timesheet(org, userId,
                month == null ? YearMonth.now(CalendarService.ZONE) : month)));
    }

    /** HR managers: sets or removes one person's record for a day, with a reason. */
    @PutMapping("/attendance/{userId}/{date}")
    public ResponseEntity<ApiResponse<DayRow>> correct(@PathVariable String org, @PathVariable UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @Valid @RequestBody AttendanceEdit in) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.correct(org, userId, date, in)));
    }

    // ── Overtime ─────────────────────────────────────────────────────────────────────

    @GetMapping("/overtime/mine")
    public ResponseEntity<ApiResponse<List<OvertimeView>>> myOvertime(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(overtime.mine(org)));
    }

    /** HR managers: everyone's. */
    @GetMapping("/overtime")
    public ResponseEntity<ApiResponse<List<OvertimeView>>> allOvertime(@PathVariable String org,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(overtime.all(org, status)));
    }

    @PostMapping("/overtime")
    public ResponseEntity<ApiResponse<OvertimeView>> requestOvertime(@PathVariable String org,
            @Valid @RequestBody OvertimeInput in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(overtime.create(org, in)));
    }

    @PostMapping("/overtime/{id}/withdraw")
    public ResponseEntity<ApiResponse<OvertimeView>> withdrawOvertime(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(overtime.withdraw(org, id)));
    }
}
