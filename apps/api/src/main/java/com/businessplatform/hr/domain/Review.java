package com.businessplatform.hr.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One person's review in a cycle: SELF (their self-assessment) → MANAGER (the reviewer's) → DONE (shared with
 * them) → ACKNOWLEDGED. Answers are [{"rating": 4, "comment": "…"}, …] in the cycle's question order.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "review")
public class Review {

    public static final String SELF = "SELF";
    public static final String MANAGER = "MANAGER";
    public static final String DONE = "DONE";
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "reviewer_id", nullable = false)
    private UUID reviewerId;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "self_answers")
    private List<Map<String, Object>> selfAnswers;

    @Column(name = "self_summary")
    private String selfSummary;

    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manager_answers")
    private List<Map<String, Object>> managerAnswers;

    @Column(name = "manager_summary")
    private String managerSummary;

    @Column(name = "overall_rating")
    private Short overallRating;

    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
