package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person's pay in a run, every figure kept as calculated. {@code adjustments}: HR's extra lines
 * [{"label": "Transport", "amount": 20.00}]; positive ones are taxable allowances, negative ones are deducted
 * after tax.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payslip")
public class Payslip {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "pay_run_id", nullable = false)
    private UUID payRunId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary;

    @Column(name = "working_days", nullable = false)
    private short workingDays;

    @Column(name = "unpaid_leave_days", nullable = false)
    private BigDecimal unpaidLeaveDays;

    @Column(name = "unpaid_leave_amount", nullable = false)
    private BigDecimal unpaidLeaveAmount;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Column(name = "overtime_amount", nullable = false)
    private BigDecimal overtimeAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> adjustments;

    @Column(nullable = false)
    private BigDecimal gross;

    @Column(nullable = false)
    private short dependants;

    @Column(name = "taxable_khr", nullable = false)
    private long taxableKhr;

    @Column(name = "tax_khr", nullable = false)
    private long taxKhr;

    @Column(nullable = false)
    private BigDecimal tax;

    @Column(name = "nssf_employee", nullable = false)
    private BigDecimal nssfEmployee;

    @Column(name = "nssf_employer", nullable = false)
    private BigDecimal nssfEmployer;

    @Column(nullable = false)
    private BigDecimal net;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
