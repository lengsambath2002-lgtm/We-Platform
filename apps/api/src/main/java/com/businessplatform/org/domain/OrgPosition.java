package com.businessplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A job in the organisation (optionally in a unit), with how many people it is planned for. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "org_position")
public class OrgPosition {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(length = 20)
    private String code;

    @Column(length = 20)
    private String grade;

    @Column(name = "reports_to_id")
    private UUID reportsToId;

    @Column(nullable = false)
    private int headcount;

    @Column(length = 2000)
    private String description;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isArchived() {
        return archivedAt != null;
    }
}
