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

/** A technician assigned to a work order. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "svc_wo_technician")
@IdClass(WoTechnician.Key.class)
public class WoTechnician {

    @Id
    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID workOrderId;
        private UUID userId;
    }
}
