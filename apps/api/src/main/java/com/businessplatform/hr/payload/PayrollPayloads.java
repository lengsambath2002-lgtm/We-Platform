package com.businessplatform.hr.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HR & Workforce: payroll. Money is USD with two decimals; tax and NSSF are also shown in riel. */
public final class PayrollPayloads {

    private PayrollPayloads() {
    }

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** {@code upToKhr} null = the last bracket (no upper limit). */
    public record BracketInput(@Min(1) Long upToKhr, @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal ratePct) {
    }

    public record SettingsRequest(@NotEmpty @Size(max = 10) List<@Valid @NotNull BracketInput> taxBrackets,
            @Min(0) @Max(100000000) int dependantAllowanceKhr, @Min(0) @Max(100000000) int nssfFloorKhr,
            @Min(0) @Max(100000000) int nssfCapKhr,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal nssfPensionEmployeePct,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal nssfPensionEmployerPct,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal nssfHealthEmployerPct,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal nssfRiskEmployerPct,
            @NotNull @DecimalMin("100") @DecimalMax("400") BigDecimal overtimeRatePct) {
    }

    public record PayRequest(@NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal monthlySalary,
            @Min(0) @Max(20) int dependants, boolean nssfEnrolled, @Size(max = 60) String bankName,
            @Size(max = 40) String bankAccount, @Size(max = 300) String reason) {
    }

    public record RunRequest(@NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "Use a month like 2026-11") String month,
            @NotNull @DecimalMin("1") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal exchangeRate,
            @Size(max = 500) String note) {
    }

    /** A DRAFT run's exchange rate and note. */
    public record RunUpdate(@NotNull @DecimalMin("1") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal exchangeRate,
            @Size(max = 500) String note) {
    }

    /** Positive amounts are taxable allowances; negative ones are deducted after tax. */
    public record Adjustment(@NotBlank @Size(max = 80) String label,
            @NotNull @DecimalMin("-100000") @DecimalMax("100000") @Digits(integer = 6, fraction = 2) BigDecimal amount) {
    }

    public record AdjustmentsRequest(@NotNull @Size(max = 20) List<@Valid @NotNull Adjustment> adjustments) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record SettingsView(List<BracketInput> taxBrackets, int dependantAllowanceKhr, int nssfFloorKhr, int nssfCapKhr,
            BigDecimal nssfPensionEmployeePct, BigDecimal nssfPensionEmployerPct, BigDecimal nssfHealthEmployerPct,
            BigDecimal nssfRiskEmployerPct, BigDecimal overtimeRatePct, Instant updatedAt) {
    }

    /** {@code monthlySalary} null = not set up yet (left out of pay runs). */
    public record PayView(PersonRef person, String department, BigDecimal monthlySalary, int dependants,
            boolean nssfEnrolled, String bankName, String bankAccount) {
    }

    public record RunTotals(int people, BigDecimal gross, BigDecimal tax, BigDecimal nssfEmployee,
            BigDecimal nssfEmployer, BigDecimal net, BigDecimal employerCost) {
    }

    public record RunSummary(UUID id, String month, BigDecimal exchangeRate, String status, String note, RunTotals totals,
            Instant createdAt, PersonRef approvedBy, Instant approvedAt, PersonRef paidBy, Instant paidAt) {
    }

    public record PayslipView(UUID id, UUID runId, String month, String runStatus, String orgName, PersonRef person,
            String department, String title, BigDecimal monthlySalary, int workingDays, BigDecimal unpaidLeaveDays,
            BigDecimal unpaidLeaveAmount, int overtimeMinutes, BigDecimal overtimeAmount, List<Adjustment> adjustments,
            BigDecimal gross, int dependants, BigDecimal exchangeRate, long taxableKhr, long taxKhr, BigDecimal tax,
            BigDecimal nssfEmployee, BigDecimal nssfEmployer, BigDecimal net, String bankName, String bankAccount,
            Instant paidAt) {
    }

    /** {@code notSetUp}: active people with no salary yet (not in the run). */
    public record RunDetail(RunSummary run, List<PayslipView> payslips, List<PersonRef> notSetUp) {
    }
}
