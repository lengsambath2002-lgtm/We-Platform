package com.businessplatform.education.domain;

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
 * A request to withdraw a student, decided through the approval engine. PENDING ─► APPROVED (the student
 * becomes WITHDRAWN on {@code effectiveOn}) | REJECTED | CANCELLED (the requester withdrew the request).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_withdrawal")
public class Withdrawal {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "effective_on", nullable = false)
    private LocalDate effectiveOn;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private String status;

    @Column(name = "approval_id")
    private UUID approvalId;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
