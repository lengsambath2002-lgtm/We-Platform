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

/** One step of someone's offboarding checklist. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "offboarding_item")
public class OffboardingItem {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "offboarding_id", nullable = false)
    private UUID offboardingId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "done_by")
    private UUID doneBy;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
