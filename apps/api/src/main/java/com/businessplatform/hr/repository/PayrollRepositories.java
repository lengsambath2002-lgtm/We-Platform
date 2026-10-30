package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.EmployeePay;
import com.businessplatform.hr.domain.PayRun;
import com.businessplatform.hr.domain.PayrollSettings;
import com.businessplatform.hr.domain.Payslip;

/** Payroll's repositories. */
public final class PayrollRepositories {

    private PayrollRepositories() {
    }

    public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, UUID> {
    }

    public interface EmployeePayRepository extends JpaRepository<EmployeePay, UUID> {

        List<EmployeePay> findByOrgId(UUID orgId);

        Optional<EmployeePay> findByOrgIdAndUserId(UUID orgId, UUID userId);
    }

    public interface PayRunRepository extends JpaRepository<PayRun, UUID> {

        List<PayRun> findByOrgIdOrderByMonthDesc(UUID orgId);

        Optional<PayRun> findByIdAndOrgId(UUID id, UUID orgId);

        boolean existsByOrgIdAndMonth(UUID orgId, String month);
    }

    public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

        List<Payslip> findByPayRunId(UUID payRunId);

        Optional<Payslip> findByIdAndOrgId(UUID id, UUID orgId);

        List<Payslip> findByOrgIdAndUserId(UUID orgId, UUID userId);
    }
}
