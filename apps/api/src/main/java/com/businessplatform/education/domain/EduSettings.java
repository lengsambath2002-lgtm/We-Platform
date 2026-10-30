package com.businessplatform.education.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The school's details for report cards and its pass mark (one row per organisation, made on first use). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_settings")
public class EduSettings {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "school_name")
    private String schoolName;

    @Column(name = "principal_name")
    private String principalName;

    private String address;

    private String phone;

    @Column(name = "pass_mark", nullable = false)
    private BigDecimal passMark;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
