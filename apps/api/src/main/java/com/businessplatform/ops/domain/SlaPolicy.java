package com.businessplatform.ops.domain;

import java.io.Serializable;
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

/** Response and resolution targets (minutes) for tickets of one priority. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "svc_sla_policy")
@IdClass(SlaPolicy.Key.class)
public class SlaPolicy {

    @Id
    @Column(name = "org_id")
    private UUID orgId;

    @Id
    private String priority;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID orgId;
        private String priority;
    }
}
