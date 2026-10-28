package com.businessplatform.retail.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A VirtualBiz shop (business) linked to one organisation; an organisation can have several shops. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "retail_shop_link")
public class ShopLink {

    /** The VirtualBiz business id. */
    @Id
    @Column(name = "business_id")
    private UUID businessId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    /** The client that reported the link. */
    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
}
