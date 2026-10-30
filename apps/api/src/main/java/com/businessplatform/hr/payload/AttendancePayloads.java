package com.businessplatform.hr.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** HR & Workforce: work schedules, attendance and overtime. Times of day are in the organisation's time zone. */
public final class AttendancePayloads {

    private AttendancePayloads() {
    }

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** {@code workDays}: ISO weekdays, 1 = Monday … 7 = Sunday. */
    public record ScheduleRequest(@NotBlank @Size(max = 60) String name, @NotNull LocalTime startTime,
            @NotNull LocalTime endTime, @Min(0) @Max(240) int breakMinutes, @Min(0) @Max(120) int graceMinutes,
            @NotEmpty List<@NotNull @Min(1) @Max(7) Integer> workDays, boolean makeDefault) {
    }

    /** {@code scheduleId} null = the organisation's default. */
    public record AssignScheduleRequest(UUID scheduleId) {
    }

    public record ClockRequest(@Size(max = 300) String note) {
    }

    /** An HR manager's correction of one person's day; without {@code clockIn} the day's record is removed. */
    public record AttendanceEdit(LocalTime clockIn, LocalTime clockOut, @Size(max = 300) String note,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record OvertimeInput(@NotNull LocalDate workDate, @Min(15) @Max(720) int minutes,
            @NotBlank @Size(max = 1000) String reason) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    /** {@code people}: how many have it as their own schedule (the default also covers everyone without one). */
    public record ScheduleView(UUID id, String name, LocalTime startTime, LocalTime endTime, int breakMinutes,
            int graceMinutes, List<Integer> workDays, boolean isDefault, int dayMinutes, long people) {
    }

    /**
     * One day of one person. {@code status}: PRESENT, LATE, ABSENT, LEAVE, HOLIDAY, DAY_OFF (not a working day, or
     * before they joined), NOT_IN (today, not clocked in yet) or UPCOMING. {@code workedMinutes} counts from
     * clock-in to clock-out, less the break on days of six hours or more; {@code overtimeMinutes} is approved overtime.
     */
    public record DayRow(LocalDate date, String status, UUID recordId, Instant clockIn, Instant clockOut,
            int lateMinutes, int workedMinutes, int overtimeMinutes, String note, boolean corrected,
            String editReason, String holidayEn, String holidayKm) {
    }

    public record Today(DayRow day, ScheduleView schedule, boolean canClockIn, boolean canClockOut) {
    }

    /** {@code expectedMinutes}: working days so far (not on leave or a holiday) times the schedule's day. */
    public record Totals(int presentDays, int lateDays, int absentDays, int leaveDays, int workedMinutes,
            int expectedMinutes, int overtimeMinutes) {
    }

    /** {@code month}: yyyy-MM. */
    public record Timesheet(PersonRef person, ScheduleView schedule, String month, List<DayRow> days, Totals totals,
            boolean canEdit) {
    }

    public record BoardRow(PersonRef person, String department, ScheduleView schedule, DayRow day) {
    }

    /** {@code reason} only for the person, their manager and HR managers. */
    public record OvertimeView(UUID id, PersonRef person, LocalDate workDate, int minutes, String reason, String status,
            UUID approvalId, String approvalStatus, Instant createdAt, boolean canWithdraw) {
    }
}
