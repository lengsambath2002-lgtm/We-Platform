package com.businessplatform.workflow.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A request for approval of one record of one module, following a workflow's steps. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_request")
public class ApprovalRequest {

    public static final String PENDING = "PENDING";
    public static final String CHANGES_REQUESTED = "CHANGES_REQUESTED";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String WITHDRAWN = "WITHDRAWN";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(nullable = false)
    private String title;

    private String summary;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(nullable = false)
    private String status;

    @Column(name = "current_step")
    private Short currentStep;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void created() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public boolean open() {
        return PENDING.equals(status) || CHANGES_REQUESTED.equals(status);
    }
}
