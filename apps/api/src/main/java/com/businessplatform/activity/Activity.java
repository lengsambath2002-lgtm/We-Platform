package com.businessplatform.activity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One thing that happened to a record (any module): who, what, details. Append-only. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "activity")
public class Activity {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "module_key", nullable = false)
    private String moduleKey;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false)
    private String verb;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> data;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
