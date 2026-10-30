package com.businessplatform.education.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A class: a section of a grade level for one academic year ("7A"), with its homeroom teacher. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_class")
public class SchoolClass {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "year_id", nullable = false)
    private UUID yearId;

    @Column(name = "grade_id", nullable = false)
    private UUID gradeId;

    @Column(nullable = false)
    private String name;

    @Column(name = "homeroom_teacher_id")
    private UUID homeroomTeacherId;

    private String room;

    private Short capacity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
