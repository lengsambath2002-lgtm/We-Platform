package com.businessplatform.education.domain;

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

/** An academic year ("2026–2027"); one of them is the organisation's current year. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "edu_year")
public class AcademicYear {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(nullable = false)
    private boolean current;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public boolean covers(LocalDate d) {
        return !d.isBefore(startsOn) && !d.isAfter(endsOn);
    }
}
