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
 * An organisation's statutory payroll settings: Tax on Salary brackets (monthly, in riel, progressive), the
 * allowance per dependant, NSSF rates and the wage range they apply to, and the overtime rate.
 * {@code taxBrackets}: [{"upToKhr": 1500000, "ratePct": 0}, …, {"upToKhr": null, "ratePct": 20}].
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payroll_settings")
public class PayrollSettings {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tax_brackets", nullable = false)
    private List<Map<String, Object>> taxBrackets;

    @Column(name = "dependant_allowance_khr", nullable = false)
    private int dependantAllowanceKhr;

    @Column(name = "nssf_floor_khr", nullable = false)
    private int nssfFloorKhr;

    @Column(name = "nssf_cap_khr", nullable = false)
    private int nssfCapKhr;

    @Column(name = "nssf_pension_employee_pct", nullable = false)
    private BigDecimal nssfPensionEmployeePct;

    @Column(name = "nssf_pension_employer_pct", nullable = false)
    private BigDecimal nssfPensionEmployerPct;

    @Column(name = "nssf_health_employer_pct", nullable = false)
    private BigDecimal nssfHealthEmployerPct;

    @Column(name = "nssf_risk_employer_pct", nullable = false)
    private BigDecimal nssfRiskEmployerPct;

    @Column(name = "overtime_rate_pct", nullable = false)
    private BigDecimal overtimeRatePct;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
