package com.businessplatform.project.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/** A project of an organisation; its tasks are numbered KEY-1, KEY-2 … */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false, length = 10)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    private String description;

    /** ACTIVE, ON_HOLD, DONE, ARCHIVED. */
    @Column(nullable = false)
    private String status = "ACTIVE";

    /** ORG (every member, not guests, can see it) or PRIVATE (project members only). */
    @Column(nullable = false)
    private String visibility = "ORG";

    @Column(name = "lead_id")
    private UUID leadId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "next_number", nullable = false)
    private int nextNumber = 1;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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
}
