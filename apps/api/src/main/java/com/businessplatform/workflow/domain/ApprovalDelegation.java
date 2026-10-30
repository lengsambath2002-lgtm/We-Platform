package com.businessplatform.workflow.domain;

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

/** While {@code userId} is away (both dates included), {@code delegateId} decides their approval steps for them. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_delegation")
public class ApprovalDelegation {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "delegate_id", nullable = false)
    private UUID delegateId;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(length = 300)
    private String reason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean covers(LocalDate day) {
        return revokedAt == null && !day.isBefore(startsOn) && !day.isAfter(endsOn);
    }
}
