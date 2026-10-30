package com.businessplatform.ops.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A job for technicians (WO-0001): from a ticket, a maintenance plan, or on its own. DRAFT ─schedule─► SCHEDULED
 * (through approval when its total is above the organisation's threshold) ─start─► IN_PROGRESS ⇄ ON_HOLD
 * ─complete (notes + customer sign-off)─► COMPLETED; anything open ─cancel─► CANCELLED.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_work_order")
public class WorkOrder {

    public static final String DRAFT = "DRAFT";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final List<String> STATUSES = List.of(DRAFT, SCHEDULED, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED);

    /** Approval states (the engine's request is {@code approvalId}). */
    public static final String NONE = "NONE";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String status;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "plan_due")
    private LocalDate planDue;

    @Column(name = "scheduled_start")
    private Instant scheduledStart;

    @Column(name = "scheduled_end")
    private Instant scheduledEnd;

    @Column(name = "parts_total", nullable = false)
    private BigDecimal partsTotal;

    @Column(name = "labour_total", nullable = false)
    private BigDecimal labourTotal;

    @Column(nullable = false)
    private BigDecimal total;

    @Column(name = "approval_state", nullable = false)
    private String approvalState;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "approved_total")
    private BigDecimal approvedTotal;

    @Column(name = "hold_reason")
    private String holdReason;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completion_notes")
    private String completionNotes;

    @Column(name = "signed_by")
    private String signedBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /** Null when the maintenance job generated it. */
    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isOpen() {
        return !COMPLETED.equals(status) && !CANCELLED.equals(status);
    }
}
