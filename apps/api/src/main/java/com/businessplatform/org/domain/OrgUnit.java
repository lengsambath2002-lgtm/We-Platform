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

/** A branch, division, department, center, section or team; the tree's top units have no parent. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "org_unit")
public class OrgUnit {

    public static final java.util.List<String> KINDS = java.util.List.of("BRANCH", "DIVISION", "DEPARTMENT", "CENTER",
            "SECTION", "TEAM");

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 12)
    private String kind;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String code;

    @Column(name = "head_user_id")
    private UUID headUserId;

    @Column(length = 300)
    private String address;

    @Column(length = 30)
    private String phone;

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
