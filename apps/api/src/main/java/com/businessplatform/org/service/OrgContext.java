package com.businessplatform.org.service;

import java.util.UUID;

import com.businessplatform.org.domain.OrgRole;

/** Who the caller is in the organisation of this request (resolved by {@link OrgAccess}). */
public record OrgContext(UUID orgId, String slug, String orgName, UUID userId, UUID membershipId, OrgRole role) {

    public boolean isAdmin() {
        return role.isAdmin();
    }

    public boolean isGuest() {
        return role == OrgRole.GUEST;
    }
}
