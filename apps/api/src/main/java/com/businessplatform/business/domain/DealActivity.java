package com.businessplatform.business.domain;

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

/** Something that happened with a deal: a call, meeting, email or note. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_deal_activity")
public class DealActivity {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String body;

    @Column(name = "happened_on", nullable = false)
    private LocalDate happenedOn;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
