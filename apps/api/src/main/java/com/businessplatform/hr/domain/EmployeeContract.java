package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An employment contract: probation, fixed-term (FDC, with an end), unlimited (UDC), internship or other. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "employee_contract")
public class EmployeeContract {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 12)
    private String kind;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "probation_ends_on")
    private LocalDate probationEndsOn;

    @Column(name = "monthly_salary")
    private BigDecimal monthlySalary;

    @Column(length = 1000)
    private String note;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
