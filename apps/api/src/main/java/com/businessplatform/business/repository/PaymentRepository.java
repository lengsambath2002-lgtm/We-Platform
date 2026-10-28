package com.businessplatform.business.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.business.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(UUID invoiceId);

    Optional<Payment> findByIdAndInvoiceId(UUID id, UUID invoiceId);
}
