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

/** A file kept on someone's employee record (ID scan, certificate …), maybe with an expiry date. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "employee_document")
public class EmployeeDocument {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 12)
    private String category;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
