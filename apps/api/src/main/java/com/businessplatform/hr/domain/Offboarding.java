package com.businessplatform.hr.domain;

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

/** Someone leaving: REQUESTED (they resigned) → IN_PROGRESS (checklist) → COMPLETED, or CANCELLED. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "offboarding")
public class Offboarding {

    public static final String REQUESTED = "REQUESTED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String kind;

    @Column(nullable = false, length = 12)
    private String status;

    @Column(name = "notice_on", nullable = false)
    private LocalDate noticeOn;

    @Column(name = "last_day", nullable = false)
    private LocalDate lastDay;

    @Column(length = 1000)
    private String reason;

    private Boolean rehire;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "started_by")
    private UUID startedBy;

    @Column(name = "completed_by")
    private UUID completedBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean open() {
        return REQUESTED.equals(status) || IN_PROGRESS.equals(status);
    }
}
