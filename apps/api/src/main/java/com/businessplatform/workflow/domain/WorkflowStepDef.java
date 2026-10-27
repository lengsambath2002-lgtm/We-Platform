package com.businessplatform.workflow.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One step of a route: a named person (USER), any Owner/Admin (ORG_ROLE), or the record's project lead. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "workflow_step_def")
public class WorkflowStepDef {

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "step_order", nullable = false)
    private short stepOrder;

    @Column(name = "approver_type", nullable = false)
    private String approverType;

    @Column(name = "approver_user")
    private UUID approverUser;

    @Column(name = "approver_role")
    private String approverRole;
}
