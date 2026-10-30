package com.businessplatform.org.service;

import java.util.Set;
import java.util.UUID;

import com.businessplatform.org.domain.OrgRole;

/** Who the caller is in the organisation of this request (resolved by {@link OrgAccess}). */
public record OrgContext(UUID orgId, String slug, String orgName, UUID userId, UUID membershipId, OrgRole role,
        Grants grants) {

    public OrgContext(UUID orgId, String slug, String orgName, UUID userId, UUID membershipId, OrgRole role) {
        this(orgId, slug, orgName, userId, membershipId, role, Grants.NONE);
    }

    public boolean isAdmin() {
        return role.isAdmin();
    }

    public boolean isGuest() {
        return role == OrgRole.GUEST;
    }

    /** Owners and Admins can do everything; members what their custom roles give them organisation-wide. */
    public boolean can(String permission) {
        return isAdmin() || grants.orgWide().contains(permission);
    }

    /** {@link #can}, or a role scoped to a unit that {@code userId} is placed in (never for oneself). */
    public boolean canFor(String permission, UUID userId) {
        return can(permission) || grants.scoped().getOrDefault(permission, Set.of()).contains(userId);
    }

    /** Whether the caller holds the permission at all: organisation-wide, or for some units (even empty ones). */
    public boolean canSome(String permission) {
        return can(permission) || grants.scoped().containsKey(permission);
    }

    /** The people a scoped permission covers; null when it covers everyone. */
    public Set<UUID> scopeOf(String permission) {
        return can(permission) ? null : grants.scoped().getOrDefault(permission, Set.of());
    }
}
