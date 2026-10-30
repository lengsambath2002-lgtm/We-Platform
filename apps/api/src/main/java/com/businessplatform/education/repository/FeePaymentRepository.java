package com.businessplatform.education.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.education.domain.*;

import jakarta.persistence.LockModeType;

public interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {

    List<FeePayment> findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(UUID invoiceId);

    Optional<FeePayment> findByIdAndInvoiceId(UUID id, UUID invoiceId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM FeePayment p WHERE p.orgId = :orgId AND p.paidOn BETWEEN :from AND :to")
    java.math.BigDecimal received(UUID orgId, LocalDate from, LocalDate to);
}
