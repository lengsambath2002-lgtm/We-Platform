package com.businessplatform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A step of a request: WAITING → PENDING → APPROVED / REJECTED / RETURNED (changes requested). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_step")
public class ApprovalStep {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "step_order", nullable = false)
    private short stepOrder;

    /** A named approver; null for an ORG_ROLE step (any active member with {@link #approverRole}). */
    @Column(name = "approver_id")
    private UUID approverId;

    @Column(name = "approver_role")
    private String approverRole;

    @Column(nullable = false)
    private String status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    /** When the step started waiting for a decision (reminders and escalation count from here). */
    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    /** Set once the step waited too long: the organisation's Admins may then decide too. */
    @Column(name = "escalated_at")
    private Instant escalatedAt;

    /** The approver an acting approver decided for. */
    @Column(name = "on_behalf_of")
    private UUID onBehalfOf;
}
