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
        // General Business: everyone but guests works with customers, quotes, invoices and their own expenses;
        // admins also void, manage products, categories and settings, and see every expense.
        if (modules.contains(Module.BUSINESS) && !ctx.isGuest()) {
            p.add("business.view");
            if (ctx.can(RoleCatalogue.BUSINESS)) {
                p.add("business.manage");
            }
        }
        // Education: admins run the school; members who teach get theirs from the module (a PermissionSource),
        // everyone else has no access to it.
        if (modules.contains(Module.EDUCATION) && ctx.can(RoleCatalogue.EDUCATION)) {
            p.add("education.view");
            p.add("education.manage");
        }
        // Service & Operations: Owners and Admins run it; members only with a module role (dispatcher or technician),
        // which the module adds through its PermissionSource. Guests get nothing.
        if (modules.contains(Module.SERVICE) && ctx.isAdmin()) {
            p.addAll(List.of("service.view", "service.manage", "service.admin"));
        }
        // HR: everyone but guests sees their own profile and leave and the team's leave; admins run HR (or members
        // with a role: HR manager organisation-wide, or for some units' people = hr.team; payroll; recruitment).
        if (modules.contains(Module.HR) && !ctx.isGuest()) {
            p.add("hr.view");
            if (ctx.can(RoleCatalogue.HR)) {
                p.add("hr.manage");
            }
            if (ctx.canSome(RoleCatalogue.HR)) {
                p.add("hr.team");
            }
            if (ctx.can(RoleCatalogue.PAYROLL)) {
                p.add("hr.payroll");
            }
        }
        // Retail & Commerce: everyone but guests sees the linked shops; admins connect and unlink them.
        if (modules.contains(Module.RETAIL) && !ctx.isGuest()) {
            p.add("retail.view");
            if (ctx.can(RoleCatalogue.RETAIL)) {
                p.add("retail.manage");
            }
        }
        if (!ctx.isGuest()) {
            p.add("calendar.create");
            p.add("reports.view");
        }
        if (ctx.isAdmin()) {
            p.addAll(List.of("org.settings.manage", "org.members.manage", "org.modules.manage",
                    "teams.manage", "workflows.manage", "org.roles.manage"));
        }
        if (ctx.can(RoleCatalogue.AUDIT)) {
            p.add("activity.view");
        }
        // Documents: the shared library for everyone but guests; admins manage anyone's files.
        if (!ctx.isGuest()) {
            p.addAll(List.of("documents.view", "documents.upload"));
        }
        if (ctx.can(RoleCatalogue.DOCUMENTS) && !ctx.isGuest()) {
            p.add("documents.manage");
        }
        // The organisation structure (units, positions, who sits where): everyone but guests reads it.
        if (!ctx.isGuest()) {
            p.add("org.structure.view");
        }
        if (ctx.can(RoleCatalogue.STRUCTURE)) {
            p.add("org.structure.manage");
        }
        if (ctx.role() == com.businessplatform.org.domain.OrgRole.OWNER) {
            p.add("org.delete");
        }
        return p;
    }
}
