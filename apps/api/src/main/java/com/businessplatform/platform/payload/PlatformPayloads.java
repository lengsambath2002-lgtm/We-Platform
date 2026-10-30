package com.businessplatform.platform.payload;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The platform console: what super admins see of every organisation and account (never an organisation's data). */
public final class PlatformPayloads {

    private PlatformPayloads() {
    }

    public record Person(UUID id, String name, String email) {
    }

    public record ModuleAdoption(String key, String nameEn, String nameKm, long organisations) {
    }

    /** {@code *30d}: in the last 30 days; {@code activeUsers30d}: used the platform (a sign-in or refresh). */
    public record Stats(long organisations, long suspendedOrganisations, long deletedOrganisations,
            long newOrganisations30d, long users, long disabledUsers, long newUsers30d, long activeUsers30d,
            long superAdmins, List<ModuleAdoption> modules) {
    }

    /** {@code status}: ACTIVE, SUSPENDED or DELETED; {@code modules}: the keys switched on. */
    public record OrgRow(UUID id, String slug, String name, String status, long members, List<Person> owners,
            List<String> modules, Instant createdAt, Instant lastActivityAt) {
    }

    public record MemberRow(UUID userId, String name, String email, String role, String status, boolean disabled,
            Instant joinedAt) {
    }

    public record ModuleRow(String key, String nameEn, String nameKm, boolean available, boolean enabled) {
    }

    public record OrgDetail(OrgRow org, Person createdBy, Instant suspendedAt, Person suspendedBy,
            String suspendedReason, Instant deletedAt, Person deletedBy, List<MemberRow> members,
            List<ModuleRow> modules) {
    }

    /** {@code status}: ACTIVE, DISABLED or LOCKED (too many wrong passwords, for a few minutes). */
    public record UserRow(UUID id, String name, String email, String avatarUrl, boolean superAdmin, String status,
            long organisations, Instant createdAt, Instant lastSeenAt) {
    }

    /** {@code orgStatus}: ACTIVE, SUSPENDED or DELETED. */
    public record UserOrg(UUID id, String slug, String name, String role, String orgStatus) {
    }

    public record UserDetail(UserRow user, String locale, Instant disabledAt, String disabledReason,
            long signedInDevices, List<UserOrg> organisations) {
    }

    /** {@code targetName}: the organisation's or person's current name. */
    public record AuditRow(UUID id, Person actor, String action, String targetType, UUID targetId,
            String targetName, Map<String, Object> data, Instant at) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record Toggle(boolean enabled) {
    }
}
