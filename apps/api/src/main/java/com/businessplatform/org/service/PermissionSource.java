package com.businessplatform.org.service;

import java.util.List;
import java.util.Set;

/**
 * Implemented by a module whose permissions depend on more than the organisation role (e.g. Education: a member
 * with a teacher profile teaches), so the client's permission list mirrors what the module's services allow —
 * without the core knowing about teachers. Must not throw; answers nothing while the module is off.
 */
public interface PermissionSource {

    List<String> permissions(OrgContext ctx, Set<String> enabledModules);
}
