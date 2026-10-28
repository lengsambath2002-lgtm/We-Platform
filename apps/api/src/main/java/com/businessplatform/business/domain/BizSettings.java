package com.businessplatform.business.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The organisation's business details: what invoices print and the defaults new documents start with. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_settings")
public class BizSettings {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "legal_name")
    private String legalName;

    private String address;

    @Column(name = "tax_id")
    private String taxId;

    private String phone;

    private String email;

    /** Bank details and payment notes printed under every invoice. */
    @Column(name = "invoice_footer")
    private String invoiceFooter;

    @Column(name = "payment_terms_days", nullable = false)
    private short paymentTermsDays;

    @Column(name = "vat_default", nullable = false)
    private boolean vatDefault;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
