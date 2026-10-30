package com.businessplatform.ops.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.ops.domain.Staff;
import com.businessplatform.ops.repository.StaffRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Who may do what in Service &amp; Operations, checked on every request. A member of the organisation (NOT_FOUND
 * otherwise) with the module switched on (MODULE_DISABLED) and a role in it (FORBIDDEN otherwise):
 * <ul>
 * <li><b>Owners and Admins</b> ({@code service.admin}): everything, plus the service staff, SLA targets and
 * settings.</li>
 * <li><b>Dispatchers</b> ({@code service.manage}): customers and sites, assets, requests and tickets, work orders
 * (create, schedule, assign, cancel), maintenance plans and reports.</li>
 * <li><b>Technicians</b> ({@code service.view} + {@code service.technician}): the tickets assigned to them and their
 * own work orders: start, hold, tick the checklist, record parts and labour, complete with the customer's
 * sign-off. Nothing else.</li>
 * </ul>
 * Members without an active staff row, and guests, get FORBIDDEN. Work orders above the organisation's threshold
 * are scheduled only after approval (default workflow "Work order approval": an Admin).
 */
@Component
@RequiredArgsConstructor
public class OpsAccess {

    /** How Service &amp; Operations records appear in the activity log and the approval engine. */
    public static final String MODULE = Module.SERVICE;
    public static final String ADMIN = "ADMIN";

    private final OrgAccess access;
    private final ModuleService modules;
    private final StaffRepository staff;
    private final MembershipRepository memberships;
    private final Clock clock;

    /** The caller in the module: their role (ADMIN, DISPATCHER or TECHNICIAN) and whether they take work orders. */
    public record Who(OrgContext ctx, String role, boolean technician) {

        public boolean admin() {
            return ADMIN.equals(role);
        }

        public boolean manager() {
            return admin() || Staff.DISPATCHER.equals(role);
        }

        public UUID userId() {
            return ctx.userId();
        }

        public UUID orgId() {
            return ctx.orgId();
        }
    }

    @Transactional(readOnly = true)
    public Who require(String slug) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), MODULE);
        return who(ctx).orElseThrow(BusinessException::forbidden);
    }

    public Who requireManager(String slug) {
        Who w = require(slug);
        if (!w.manager()) {
            throw BusinessException.forbidden();
        }
        return w;
    }

    public Who requireAdmin(String slug) {
        Who w = require(slug);
        if (!w.admin()) {
            throw BusinessException.forbidden();
        }
        return w;
    }

    /** The caller's role, or empty without one (guests, members without an active staff row). Doesn't check the module. */
    @Transactional(readOnly = true)
    public Optional<Who> who(OrgContext ctx) {
        if (ctx.isGuest()) {
            return Optional.empty();
        }
        Optional<Staff> s = staff.findByOrgIdAndUserId(ctx.orgId(), ctx.userId()).filter(Staff::isActive);
        boolean tech = s.map(x -> Staff.TECHNICIAN.equals(x.getRole())).orElse(false);
        if (ctx.isAdmin()) {
            return Optional.of(new Who(ctx, ADMIN, tech));
        }
        // A custom role with Service management makes a member a dispatcher, whatever their staff row says.
        if (ctx.can(RoleCatalogue.SERVICE)) {
            return Optional.of(new Who(ctx, Staff.DISPATCHER, tech));
        }
        return s.map(x -> new Who(ctx, x.getRole(), tech));
    }

    /** {@link #who} when the module is on (for the core's hooks, which must not throw). */
    @Transactional(readOnly = true)
    public Optional<Who> usable(OrgContext ctx) {
        return modules.enabled(ctx.orgId()).contains(MODULE) ? who(ctx) : Optional.empty();
    }

    /** Whether {@code userId} works in the module (an active Owner / Admin, or active service staff): tickets go to them. */
    @Transactional(readOnly = true)
    public boolean works(UUID orgId, UUID userId) {
        Optional<Membership> m = memberships.findByOrgIdAndUserId(orgId, userId).filter(x -> Membership.ACTIVE.equals(x.getStatus()));
        if (m.isEmpty() || m.get().getRole() == com.businessplatform.org.domain.OrgRole.GUEST) {
            return false;
        }
        return m.get().getRole().isAdmin() || staff.findByOrgIdAndUserId(orgId, userId).filter(Staff::isActive).isPresent();
    }

    /** Whether {@code userId} is an active member with an active technician profile: work orders go to them. */
    @Transactional(readOnly = true)
    public boolean technician(UUID orgId, UUID userId) {
        boolean member = memberships.findByOrgIdAndUserId(orgId, userId)
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()) && x.getRole() != com.businessplatform.org.domain.OrgRole.GUEST)
                .isPresent();
        return member && staff.findByOrgIdAndUserId(orgId, userId)
                .filter(x -> x.isActive() && Staff.TECHNICIAN.equals(x.getRole())).isPresent();
    }

    /** Today in the organisation's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }
}
