package com.businessplatform.org.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.businessplatform.module.domain.Module;

/**
 * Permission codes for the client's navigation and buttons, from the organisation role and the
 * switched-on modules. The services check the same rules on every request; this list is a mirror.
 */
public final class Permissions {

    private Permissions() {
    }

    public static List<String> of(OrgContext ctx, Set<String> modules) {
        List<String> p = new ArrayList<>(List.of("org.view", "calendar.view", "approvals.view", "teams.view"));
        if (modules.contains(Module.PROJECTS)) {
            p.add("projects.view");
            if (!ctx.isGuest()) {
                p.add("projects.create");
            }
        }
        if (!ctx.isGuest()) {
            p.add("calendar.create");
            p.add("reports.view");
        }
        if (ctx.isAdmin()) {
            p.addAll(List.of("org.settings.manage", "org.members.manage", "org.modules.manage",
                    "teams.manage", "workflows.manage", "activity.view"));
        }
        // Documents: the shared library for everyone but guests; admins manage anyone's files.
        if (!ctx.isGuest()) {
            p.addAll(List.of("documents.view", "documents.upload"));
        }
        if (ctx.isAdmin()) {
            p.add("documents.manage");
        }
        return p;
    }
}
