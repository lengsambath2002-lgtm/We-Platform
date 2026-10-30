package com.businessplatform.education.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A grade level ("Grade 7"), ordered by {@code position}. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_grade")
public class GradeLevel {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_km")
    private String nameKm;

    @Column(nullable = false)
    private short position;

    @Column(nullable = false)
    private boolean active;
}
