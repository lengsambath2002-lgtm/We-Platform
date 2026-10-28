package com.businessplatform.business.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Who may do what in General Business, checked on every request. A member of the organisation (NOT_FOUND
 * otherwise) with the module switched on (MODULE_DISABLED), never a guest (FORBIDDEN).
 * <ul>
 * <li><b>Members</b> ({@code business.view}): see customers, products, quotes and invoices; add and edit
 * customers; create quotes and invoices, and edit, delete, send, issue and take payments on <em>their own</em>
 * (a quote's accept / decline and conversion too); record their own expenses and send them for approval; see
 * only their own expenses.</li>
 * <li><b>Owners and Admins</b> ({@code business.manage}): all of that on anyone's documents, plus archiving
 * customers, products and prices, voiding invoices and removing payments, every expense (and marking approved
 * ones paid), expense categories and the business settings; invoice due dates on their calendar.</li>
 * </ul>
 * Expenses are decided by the approval engine (default workflow "Expense approval": an Admin).
 */
@Component
@RequiredArgsConstructor
public class BusinessAccess {

    /** How General Business records appear in the activity log and the approval engine. */
    public static final String MODULE = Module.BUSINESS;

    private final OrgAccess access;
    private final ModuleService modules;
    private final Clock clock;

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

    /** Whether the module is usable for {@code ctx} (for the core's hooks, which must not throw). */
    public boolean usable(OrgContext ctx) {
        return !ctx.isGuest() && modules.enabled(ctx.orgId()).contains(MODULE);
    }

    public static boolean manages(OrgContext ctx) {
        return ctx.isAdmin();
    }

    /** Admins, or the person who created the record. */
    public static boolean owns(OrgContext ctx, UUID createdBy) {
        return manages(ctx) || ctx.userId().equals(createdBy);
    }

    /** FORBIDDEN unless {@link #owns}. */
    public static void requireOwner(OrgContext ctx, UUID createdBy) {
        if (!owns(ctx, createdBy)) {
            throw BusinessException.forbidden();
        }
    }

    /** Today in the organisation's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }
}
