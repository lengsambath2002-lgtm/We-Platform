package com.businessplatform.module.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Whether an organisation has a module switched on. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "org_module")
@IdClass(OrgModule.Key.class)
public class OrgModule {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Id
    @Column(name = "module_key")
    private String moduleKey;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID orgId;
        private String moduleKey;
    }
}
