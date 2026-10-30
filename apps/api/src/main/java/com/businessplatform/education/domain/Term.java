package com.businessplatform.education.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A term (semester) of an academic year; assessments and results belong to one. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_term")
public class Term {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "year_id", nullable = false)
    private UUID yearId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    public boolean covers(LocalDate d) {
        return !d.isBefore(startsOn) && !d.isAfter(endsOn);
    }
}
