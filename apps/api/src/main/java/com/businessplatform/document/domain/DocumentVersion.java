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

/** One uploaded version of a document (the current one included); never changed afterwards. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_version")
public class DocumentVersion {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int version;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    private String note;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
