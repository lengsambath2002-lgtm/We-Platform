package com.businessplatform.org.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a member's custom roles give them: permissions for the whole organisation, and scoped permissions with the
 * people (user ids, never their own) they apply to. {@code roles}: the roles' names, for display.
 */
public record Grants(Set<String> orgWide, Map<String, Set<UUID>> scoped, List<String> roles) {

    public static final Grants NONE = new Grants(Set.of(), Map.of(), List.of());
}
