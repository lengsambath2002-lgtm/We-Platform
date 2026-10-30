package com.businessplatform.ops.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One thing to check or do on a work order, ticked off by its technician. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_wo_checklist")
public class ChecklistItem {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean done;

    @Column(name = "done_by")
    private UUID doneBy;

    @Column(name = "done_at")
    private Instant doneAt;
}
