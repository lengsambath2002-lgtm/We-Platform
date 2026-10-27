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

/** The request's history: every submit, decision, resubmission and withdrawal, with its comment. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_action")
public class ApprovalAction {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "step_order")
    private Short stepOrder;

    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
