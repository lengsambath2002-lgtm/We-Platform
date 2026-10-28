package com.businessplatform.hr.service;

import org.springframework.stereotype.Component;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Who may use HR in this request: a member (NOT_FOUND otherwise), with the module switched on
 * (MODULE_DISABLED), and not a guest (FORBIDDEN). HR managers are the organisation's Owners and Admins
 * ({@code hr.manage}); everyone else has {@code hr.view}: their own profile and leave, the directory and
 * the team's absences.
 */
@Component
@RequiredArgsConstructor
public class HrAccess {

    /** How HR records appear in the activity log and the approval engine. */
    public static final String MODULE = Module.HR;

    private final OrgAccess access;
    private final ModuleService modules;

    public OrgContext require(String slug) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), MODULE);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    public OrgContext requireManager(String slug) {
        OrgContext ctx = require(slug);
        if (!manages(ctx)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    /** Whether HR is usable for {@code ctx} (for the core's hooks, which must not throw). */
    public boolean usable(OrgContext ctx) {
        return !ctx.isGuest() && modules.enabled(ctx.orgId()).contains(MODULE);
    }

    public static boolean manages(OrgContext ctx) {
        return ctx.isAdmin();
    }
}
