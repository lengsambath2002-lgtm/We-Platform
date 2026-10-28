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

/**
 * An authorization code (stored only as its SHA-256), single use, 60 seconds, bound to the client,
 * redirect URI, user, PKCE challenge (S256), nonce, scope and the time the user signed in.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "oidc_code")
public class OidcCode {

    @Id
    private UUID id;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(name = "redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_challenge", nullable = false, length = 128)
    private String codeChallenge;

    @Column(nullable = false, length = 255)
    private String nonce;

    @Column(nullable = false, length = 200)
    private String scope;

    @Column(name = "auth_time", nullable = false)
    private Instant authTime;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
