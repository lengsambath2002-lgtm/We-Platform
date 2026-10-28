package com.businessplatform.hr.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HR & Workforce: profiles, leave types, balances and leave requests. */
public final class HrPayloads {

    private HrPayloads() {
    }

    private static final String PHONE = "^[0-9+()\\-. ]{6,30}$";

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** An HR manager's full edit of a profile; empty or null clears a field. */
    public record ProfileRequest(@Size(max = 80) String title, @Size(max = 80) String department, UUID managerId,
            @Pattern(regexp = "^$|FULL_TIME|PART_TIME|CONTRACT|INTERN") String employmentType, LocalDate startDate,
            @Pattern(regexp = "^$|" + PHONE, message = "Use digits, spaces and + ( ) - only") String workPhone,
            @Size(max = 80) String emergencyName,
            @Pattern(regexp = "^$|" + PHONE, message = "Use digits, spaces and + ( ) - only") String emergencyPhone,
            @Size(max = 40) String emergencyRelation) {
    }

    /** What anyone may change on their own profile; empty or null clears a field. */
    public record ContactRequest(@Pattern(regexp = "^$|" + PHONE, message = "Use digits, spaces and + ( ) - only") String workPhone,
            @Size(max = 80) String emergencyName,
            @Pattern(regexp = "^$|" + PHONE, message = "Use digits, spaces and + ( ) - only") String emergencyPhone,
            @Size(max = 40) String emergencyRelation) {
    }

    /** {@code allowanceDays} null = unlimited. */
    public record LeaveTypeRequest(@NotBlank @Size(max = 60) String nameEn, @NotBlank @Size(max = 60) String nameKm,
            boolean paid, @DecimalMin("0") @DecimalMax("366") BigDecimal allowanceDays, boolean halfDays,
            Boolean active) {
    }

    public record AdjustRequest(@NotNull UUID userId, @NotNull UUID leaveTypeId, @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull @DecimalMin("-366") @DecimalMax("366") BigDecimal days, @NotBlank @Size(max = 500) String reason) {
    }

    public record LeaveInput(@NotNull UUID leaveTypeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
            boolean halfStart, boolean halfEnd, @Size(max = 2000) String reason) {
    }

    public record CancelRequest(@Size(max = 500) String reason) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    /** A directory row: what every member may see about a colleague. */
    public record PersonSummary(PersonRef person, String email, String title, String department, PersonRef manager,
            String workPhone) {
    }

    /**
     * A profile. Employment details and the emergency contact are null unless the caller is the person or
     * an HR manager ({@code privateShown}).
     */
    public record ProfileView(PersonRef person, String email, String role, String title, String department,
            PersonRef manager, String workPhone, boolean privateShown, String employmentType, LocalDate startDate,
            String emergencyName, String emergencyPhone, String emergencyRelation, List<PersonRef> reports,
            boolean canEdit, boolean canEditContact) {
    }

    public record HolidayRequest(@NotNull LocalDate date, @NotBlank @Size(max = 120) String nameEn,
            @Size(max = 120) String nameKm) {
    }

    public record HolidayView(UUID id, LocalDate date, String nameEn, String nameKm) {
    }

    /** {@code added}: new days; {@code kept}: days that already had a holiday. */
    public record HolidayImportResult(int added, int kept) {
    }

    public record LeaveTypeView(UUID id, String nameEn, String nameKm, boolean paid, BigDecimal allowanceDays,
            boolean halfDays, boolean active) {
    }

    /** One leave type for one person and year; {@code allowance}/{@code remaining} null = unlimited. */
    public record Balance(LeaveTypeView type, int year, BigDecimal allowance, BigDecimal adjusted, BigDecimal used,
            BigDecimal pending, BigDecimal remaining) {
    }

    public record AdjustmentView(UUID id, LeaveTypeView type, int year, BigDecimal days, String reason, PersonRef by,
            Instant at) {
    }

    /**
     * A leave request. {@code approvalStatus}: the engine's status of its approval request (PENDING,
     * CHANGES_REQUESTED …), null when there is none. {@code reason} is null unless the caller is the
     * person, their manager or an HR manager.
     */
    public record LeaveView(UUID id, PersonRef person, LeaveTypeView type, LocalDate startDate, LocalDate endDate,
            boolean halfStart, boolean halfEnd, BigDecimal days, String reason, String status, UUID approvalId,
            String approvalStatus, String cancelReason, Instant createdAt, boolean canWithdraw, boolean canCancel) {
    }

    /** Someone away on some of the asked days (the leave type only for HR managers and the person). */
    public record Absence(UUID leaveId, PersonRef person, LocalDate startDate, LocalDate endDate, boolean halfStart,
            boolean halfEnd, LeaveTypeView type) {
    }
}
