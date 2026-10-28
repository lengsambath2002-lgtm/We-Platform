package com.businessplatform.document.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A file in the library; {@code version} and {@code fileId} are the current version's. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document")
public class Document {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private int version;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "updated_by", nullable = false)
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;
}
