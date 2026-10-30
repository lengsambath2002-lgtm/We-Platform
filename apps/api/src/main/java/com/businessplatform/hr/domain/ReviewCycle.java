package com.businessplatform.hr.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/** A review round: DRAFT (HR prepares the questions) → OPEN (everyone has a review) → CLOSED. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "review_cycle")
public class ReviewCycle {

    public static final String DRAFT = "DRAFT";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** [{"text": "Quality of work"}, …]. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Map<String, Object>> questions;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "launched_at")
    private Instant launchedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
