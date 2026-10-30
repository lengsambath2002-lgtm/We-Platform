package com.businessplatform.ops.domain;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service request or ticket (TCK-0001). NEW → TRIAGED → IN_PROGRESS ⇄ WAITING → RESOLVED → CLOSED; RESOLVED and
 * CLOSED can be reopened. The SLA times come from the priority's policy; breached / at risk is derived by {@link Sla}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_ticket")
public class Ticket {

    public static final String NEW = "NEW";
    public static final String TRIAGED = "TRIAGED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String WAITING = "WAITING";
    public static final String RESOLVED = "RESOLVED";
    public static final String CLOSED = "CLOSED";
    public static final List<String> STATUSES = List.of(NEW, TRIAGED, IN_PROGRESS, WAITING, RESOLVED, CLOSED);
    public static final Set<String> OPEN = Set.of(NEW, TRIAGED, IN_PROGRESS, WAITING);
    public static final List<String> CATEGORIES = List.of("REPAIR", "INSTALLATION", "MAINTENANCE", "INSPECTION",
            "COMPLAINT", "OTHER");

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "requester_name")
    private String requesterName;

    @Column(name = "requester_phone")
    private String requesterPhone;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String status;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "response_due_at", nullable = false)
    private Instant responseDueAt;

    @Column(name = "response_warn_at", nullable = false)
    private Instant responseWarnAt;

    @Column(name = "resolution_due_at", nullable = false)
    private Instant resolutionDueAt;

    @Column(name = "resolution_warn_at", nullable = false)
    private Instant resolutionWarnAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isOpen() {
        return OPEN.contains(status);
    }
}
