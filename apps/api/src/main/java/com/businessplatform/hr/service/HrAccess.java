package com.businessplatform.hr.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

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

    /** An HR manager for at least some people (organisation-wide, or a role scoped to units). */
    public OrgContext requireTeam(String slug) {
        OrgContext ctx = require(slug);
        if (!ctx.canSome(RoleCatalogue.HR)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    public OrgContext requireRecruiter(String slug) {
        OrgContext ctx = require(slug);
        if (!recruits(ctx)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    public OrgContext requirePayroll(String slug) {
        OrgContext ctx = require(slug);
        if (!runsPayroll(ctx)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    /** Whether HR is usable for {@code ctx} (for the core's hooks, which must not throw). */
    public boolean usable(OrgContext ctx) {
        return !ctx.isGuest() && modules.enabled(ctx.orgId()).contains(MODULE);
    }

    /** An HR manager for everyone: Owners, Admins and members with an organisation-wide HR manager role. */
    public static boolean manages(OrgContext ctx) {
        return ctx.can(RoleCatalogue.HR);
    }

    /** An HR manager for this person: for everyone, or through a role scoped to a unit they are placed in. */
    public static boolean managesPerson(OrgContext ctx, UUID userId) {
        return ctx.canFor(RoleCatalogue.HR, userId);
    }

    /** Payroll (salaries, runs, payslips of others) is its own permission, apart from managing people. */
    public static boolean runsPayroll(OrgContext ctx) {
        return ctx.can(RoleCatalogue.PAYROLL);
    }

    /** Recruitment for every job opening (hiring managers and panels see their own ones regardless). */
    public static boolean recruits(OrgContext ctx) {
        return manages(ctx) || ctx.can(RoleCatalogue.RECRUIT);
    }
}
