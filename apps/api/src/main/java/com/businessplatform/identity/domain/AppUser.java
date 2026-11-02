package com.businessplatform.identity.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One account per person, for every organisation and module. Email is stored lower-cased. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 5)
    private String locale = "en";

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "failed_logins", nullable = false)
    private short failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Runs the platform: the console at /admin, every organisation and account. */
    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin;

    /** Set by a super admin: the account cannot sign in until enabled again. */
    /** Two-step sign-in: the authenticator secret (encrypted, {@code SecretBox}); null while off. */
    @Column(name = "totp_secret", length = 200)
    private String totpSecret;

    /** A secret being set up, until the first code confirms it. */
    @Column(name = "totp_pending_secret", length = 200)
    private String totpPendingSecret;

    @Column(name = "totp_enabled_at")
    private Instant totpEnabledAt;

    @Column(name = "totp_last_step")
    private Long totpLastStep;

    public boolean isTwoStepOn() {
        return totpEnabledAt != null;
    }

    /** When the account confirmed its email (an emailed link); null until then. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** The person deleted their account: email and sign-in erased (see {@code AccountDeletionService}). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Column(name = "disabled_reason", length = 500)
    private String disabledReason;

    public boolean isDisabled() {
        return disabledAt != null;
    }

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void created() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }
}
