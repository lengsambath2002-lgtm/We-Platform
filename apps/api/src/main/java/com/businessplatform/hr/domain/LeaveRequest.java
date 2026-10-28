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

/**
 * Someone's request for time off. Decided through the approval engine ({@code approvalId}):
 * PENDING ─approved─► APPROVED ─cancelled before it starts─► CANCELLED; PENDING ─► REJECTED / WITHDRAWN.
 * PENDING and APPROVED days count against the balance; the others give them back.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "leave_type_id", nullable = false)
    private UUID leaveTypeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** The first day starts at midday (afternoon only). */
    @Column(name = "half_start", nullable = false)
    private boolean halfStart;

    /** The last day ends at midday (morning only). */
    @Column(name = "half_end", nullable = false)
    private boolean halfEnd;

    @Column(nullable = false)
    private BigDecimal days;

    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Counts against the balance. */
    public boolean holdsDays() {
        return PENDING.equals(status) || APPROVED.equals(status);
    }
}
