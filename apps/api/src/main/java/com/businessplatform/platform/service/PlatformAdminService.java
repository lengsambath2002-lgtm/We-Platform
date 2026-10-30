package com.businessplatform.platform.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.repository.UserSessionRepository;
import com.businessplatform.identity.service.AccountService;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.OrganisationRepository;
import com.businessplatform.platform.domain.PlatformAudit;
import com.businessplatform.platform.payload.PlatformPayloads.AuditRow;
import com.businessplatform.platform.payload.PlatformPayloads.ModuleAdoption;
import com.businessplatform.platform.payload.PlatformPayloads.ModuleRow;
import com.businessplatform.platform.payload.PlatformPayloads.OrgDetail;
import com.businessplatform.platform.payload.PlatformPayloads.OrgRow;
import com.businessplatform.platform.payload.PlatformPayloads.Person;
import com.businessplatform.platform.payload.PlatformPayloads.Stats;
import com.businessplatform.platform.payload.PlatformPayloads.UserDetail;
import com.businessplatform.platform.payload.PlatformPayloads.UserRow;
import com.businessplatform.platform.repository.PlatformAuditRepository;
import com.businessplatform.platform.repository.PlatformRepository;

import lombok.RequiredArgsConstructor;

/**
 * The platform console, for super admins only (everyone else gets NOT_FOUND, so the console isn't revealed).
 * Super admins see who uses the platform and switch things off: suspend an organisation, disable an account,
 * sign it out, send it a password reset, switch an organisation's modules. They never see an organisation's
 * own data (tasks, documents, invoices …). Every change goes to the platform audit log.
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    static final int MAX_PAGE = 100;
    static final Duration RECENT = Duration.ofDays(30);

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final OrganisationRepository orgs;
    private final PlatformRepository platform;
    private final PlatformAuditRepository audit;
    private final ModuleService modules;
    private final ActivityService activity;
    private final AccountService accounts;
    private final Clock clock;

    /** The signed-in super admin's id; NOT_FOUND for anyone else. */
    public UUID requireSuperAdmin() {
        return users.findById(CurrentUser.id()).filter(u -> u.isSuperAdmin() && !u.isDisabled()).map(AppUser::getId)
                .orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        requireSuperAdmin();
        PlatformRepository.Counts c = platform.counts(clock.instant().minus(RECENT));
        Map<String, Long> adoption = platform.moduleAdoption();
        List<ModuleAdoption> mods = modules.registry().stream()
                .map(m -> new ModuleAdoption(m.getKey(), m.getNameEn(), m.getNameKm(), adoption.getOrDefault(m.getKey(), 0L)))
                .toList();
        return new Stats(c.organisations(), c.suspendedOrganisations(), c.deletedOrganisations(), c.newOrganisations(),
                c.users(), c.disabledUsers(), c.newUsers(), c.activeUsers(), c.superAdmins(), mods);
    }

    // ── Organisations ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<OrgRow> organisations(String q, String status, int page, int size) {
        requireSuperAdmin();
        int s = pageSize(size);
        PlatformRepository.Page<OrgRow> p = platform.organisations(q, status, Math.max(page, 0), s);
        return new PageResponse<>(p.items(), Math.max(page, 0), s, p.total());
    }

    /** Opening an organisation (its members included) is logged: platform staff looking at a customer. */
    @Transactional
    public OrgDetail organisation(UUID id) {
        UUID me = requireSuperAdmin();
        Organisation o = org(id);
        record(me, "org.viewed", PlatformAudit.ORGANISATION, o.getId(), Map.of());
        return detail(o);
    }

    /** Closes the organisation to its members (they see why it is closed, not the reason); its data is kept. */
    @Transactional
    public OrgDetail suspend(UUID id, String reason) {
        UUID me = requireSuperAdmin();
        Organisation o = org(id);
        if (o.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the organisation first", List.of());
        }
        if (o.isSuspended()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The organisation is already suspended", List.of());
        }
        o.setSuspendedAt(clock.instant());
        o.setSuspendedBy(me);
        o.setSuspendedReason(reason.strip());
        record(me, "org.suspended", PlatformAudit.ORGANISATION, o.getId(), Map.of("reason", o.getSuspendedReason()));
        return detail(o);
    }

    @Transactional
    public OrgDetail reactivate(UUID id) {
        UUID me = requireSuperAdmin();
        Organisation o = org(id);
        if (!o.isSuspended()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The organisation is not suspended", List.of());
        }
        o.setSuspendedAt(null);
        o.setSuspendedBy(null);
        o.setSuspendedReason(null);
        record(me, "org.reactivated", PlatformAudit.ORGANISATION, o.getId(), Map.of());
        return detail(o);
    }

    /** Undoes an owner's deletion: the organisation and its data come back as they were (invitations stay revoked). */
    @Transactional
    public OrgDetail restore(UUID id) {
        UUID me = requireSuperAdmin();
        Organisation o = org(id);
        if (!o.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The organisation is not deleted", List.of());
        }
        o.setDeletedAt(null);
        o.setDeletedBy(null);
        record(me, "org.restored", PlatformAudit.ORGANISATION, o.getId(), Map.of());
        return detail(o);
    }

    /** Switches a module on or off for the organisation; its own audit log shows it too. */
    @Transactional
    public OrgDetail setModule(UUID id, String key, boolean on) {
        UUID me = requireSuperAdmin();
        Organisation o = org(id);
        if (o.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the organisation first", List.of());
        }
        modules.set(o.getId(), key, on, me);
        String verb = on ? "org.module.enabled" : "org.module.disabled";
        activity.record(o.getId(), "org", "module", o.getId(), me, verb, Map.of("module", key));
        record(me, verb, PlatformAudit.ORGANISATION, o.getId(), Map.of("module", key));
        return detail(o);
    }

    private Organisation org(UUID id) {
        return orgs.findById(id).orElseThrow(BusinessException::notFound);
    }

    private OrgDetail detail(Organisation o) {
        orgs.flush();
        OrgRow row = platform.organisation(o.getId());
        var on = modules.enabled(o.getId());
        List<ModuleRow> mods = modules.registry().stream()
                .map(m -> new ModuleRow(m.getKey(), m.getNameEn(), m.getNameKm(), modules.available(m), on.contains(m.getKey())))
                .toList();
        return new OrgDetail(row, person(o.getCreatedBy()), o.getSuspendedAt(), person(o.getSuspendedBy()),
                o.getSuspendedReason(), o.getDeletedAt(), person(o.getDeletedBy()), platform.members(o.getId()), mods);
    }

    // ── Accounts ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<UserRow> users(String q, String status, int page, int size) {
        requireSuperAdmin();
        int s = pageSize(size);
        PlatformRepository.Page<UserRow> p = platform.users(q, status, clock.instant(), Math.max(page, 0), s);
        return new PageResponse<>(p.items(), Math.max(page, 0), s, p.total());
    }

    /** Opening someone's account (their organisations included) is logged too. */
    @Transactional
    public UserDetail user(UUID id) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        record(me, "user.viewed", PlatformAudit.USER, u.getId(), Map.of());
        return detail(u);
    }

    /** Refuses sign-in and signs the account out everywhere at once. Not yourself, and not a super admin. */
    @Transactional
    public UserDetail disable(UUID id, String reason) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        if (u.getId().equals(me)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "You cannot disable your own account", List.of());
        }
        if (u.isSuperAdmin()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Remove super admin from this account first", List.of());
        }
        if (u.isDisabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The account is already disabled", List.of());
        }
        Instant now = clock.instant();
        u.setDisabledAt(now);
        u.setDisabledReason(reason.strip());
        sessions.revokeAll(u.getId(), now);
        record(me, "user.disabled", PlatformAudit.USER, u.getId(), Map.of("reason", u.getDisabledReason()));
        return detail(u);
    }

    @Transactional
    public UserDetail enable(UUID id) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        if (!u.isDisabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The account is not disabled", List.of());
        }
        u.setDisabledAt(null);
        u.setDisabledReason(null);
        u.setFailedLogins((short) 0);
        u.setLockedUntil(null);
        record(me, "user.enabled", PlatformAudit.USER, u.getId(), Map.of());
        return detail(u);
    }

    /** Ends every signed-in device of the account (e.g. a lost phone); it can sign in again. */
    @Transactional
    public UserDetail signOut(UUID id) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        int n = sessions.revokeAll(u.getId(), clock.instant());
        record(me, "user.signed_out", PlatformAudit.USER, u.getId(), Map.of("sessions", n));
        return detail(u);
    }

    /** Emails the account a password-reset link, as "forgot password" does; the super admin never sees the link. */
    @Transactional
    public UserDetail sendPasswordReset(UUID id, String ip) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        if (u.isDisabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Enable the account first", List.of());
        }
        accounts.forgotPassword(u.getEmail(), ip);
        record(me, "user.password_reset_sent", PlatformAudit.USER, u.getId(), Map.of());
        return detail(u);
    }

    /** Grants or revokes super admin. Nobody revokes their own, so the platform always keeps one. */
    @Transactional
    public UserDetail setSuperAdmin(UUID id, boolean on) {
        UUID me = requireSuperAdmin();
        AppUser u = account(id);
        if (!on && u.getId().equals(me)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "You cannot remove super admin from yourself", List.of());
        }
        if (on && u.isDisabled()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Enable the account first", List.of());
        }
        if (u.isSuperAdmin() != on) {
            u.setSuperAdmin(on);
            record(me, on ? "user.super_admin.granted" : "user.super_admin.revoked", PlatformAudit.USER, u.getId(), Map.of());
        }
        return detail(u);
    }

    private AppUser account(UUID id) {
        return users.findById(id).orElseThrow(BusinessException::notFound);
    }

    private UserDetail detail(AppUser u) {
        users.flush();
        Instant now = clock.instant();
        return new UserDetail(platform.user(u.getId(), now), u.getLocale(), u.getDisabledAt(), u.getDisabledReason(),
                sessions.findActive(u.getId(), now).size(), platform.userOrgs(u.getId()));
    }

    // ── Audit ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<AuditRow> audit(UUID targetId, int page, int size) {
        requireSuperAdmin();
        int s = pageSize(size);
        PlatformRepository.Page<AuditRow> p = platform.audit(targetId, Math.max(page, 0), s);
        return new PageResponse<>(p.items(), Math.max(page, 0), s, p.total());
    }

    private void record(UUID actor, String action, String targetType, UUID targetId, Map<String, Object> data) {
        PlatformAudit a = new PlatformAudit();
        a.setId(Ids.next());
        a.setActorId(actor);
        a.setAction(action);
        a.setTargetType(targetType);
        a.setTargetId(targetId);
        a.setData(data);
        a.setCreatedAt(clock.instant());
        audit.save(a);
    }

    private Person person(UUID id) {
        return id == null ? null : users.findById(id).map(u -> new Person(u.getId(), u.getName(), u.getEmail())).orElse(null);
    }

    private static int pageSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE);
    }
}
