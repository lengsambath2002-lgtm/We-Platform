package com.businessplatform.hr.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What one person is paid: monthly salary (USD), dependants for Tax on Salary, NSSF enrolment, bank account. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "employee_pay")
public class EmployeePay {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "monthly_salary", nullable = false)
    private BigDecimal monthlySalary;

    @Column(nullable = false)
    private short dependants;

    @Column(name = "nssf_enrolled", nullable = false)
    private boolean nssfEnrolled;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
