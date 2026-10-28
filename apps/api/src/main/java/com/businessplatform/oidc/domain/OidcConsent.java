package com.businessplatform.oidc.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A person's approval for a client to sign them in, with the scopes granted; one per (user, client). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "oidc_consent")
public class OidcConsent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    /** Space-separated. */
    @Column(nullable = false, length = 200)
    private String scopes;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
