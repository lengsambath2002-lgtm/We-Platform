package com.businessplatform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A signed-in device: its refresh token (hashed), rotated on every refresh into a new row of the same
 * {@code familyId}. The family is what the account page lists and signs out.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_session")
public class UserSession {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(length = 64)
    private String ip;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;
}
