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
        // HR: everyone but guests sees their own profile and leave and the team's leave; admins run HR.
        if (modules.contains(Module.HR) && !ctx.isGuest()) {
            p.add("hr.view");
            if (ctx.isAdmin()) {
                p.add("hr.manage");
            }
        }
        // Retail & Commerce: everyone but guests sees the linked shops; admins connect and unlink them.
        if (modules.contains(Module.RETAIL) && !ctx.isGuest()) {
            p.add("retail.view");
            if (ctx.isAdmin()) {
                p.add("retail.manage");
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
        if (ctx.role() == com.businessplatform.org.domain.OrgRole.OWNER) {
            p.add("org.delete");
        }
        return p;
    }
}
