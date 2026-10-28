package com.businessplatform.business.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.DocLine;

public interface DocLineRepository extends JpaRepository<DocLine, UUID> {

    List<DocLine> findByQuoteIdOrderByPosition(UUID quoteId);

    List<DocLine> findByInvoiceIdOrderByPosition(UUID invoiceId);

    @Modifying
    @Query("DELETE FROM DocLine l WHERE l.quoteId = :quoteId")
    void deleteByQuote(UUID quoteId);

    @Modifying
    @Query("DELETE FROM DocLine l WHERE l.invoiceId = :invoiceId")
    void deleteByInvoice(UUID invoiceId);
}
