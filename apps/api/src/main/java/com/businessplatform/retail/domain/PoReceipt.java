package com.businessplatform.retail.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/** One delivery against a purchase order: [{"lineId": "…", "quantity": 5}]; {@code stockRecorded}: put into VirtualBiz stock. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_po_receipt")
public class PoReceipt {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "po_id", nullable = false)
    private UUID poId;

    @Column(name = "received_on", nullable = false)
    private LocalDate receivedOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> lines;

    private String note;

    @Column(name = "stock_recorded", nullable = false)
    private boolean stockRecorded;

    @Column(name = "received_by", nullable = false)
    private UUID receivedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
