package com.businessplatform.org.domain;

/** A member's role in an organisation (docs/02-architecture.md §5). */
public enum OrgRole {
    OWNER, ADMIN, MEMBER, GUEST;

    public boolean isAdmin() {
        return this == OWNER || this == ADMIN;
    }
}
