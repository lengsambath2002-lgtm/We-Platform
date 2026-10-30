package com.businessplatform.billing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.billing.domain.Plan;
import com.businessplatform.billing.domain.Subscription;
import com.businessplatform.billing.payload.BillingPayloads.*;
import com.businessplatform.billing.repository.BillingRepositories.Plans;
import com.businessplatform.billing.repository.BillingRepositories.Subscriptions;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.file.StorageQuota;
import com.businessplatform.module.service.ModuleEntitlements;
import com.businessplatform.org.domain.Invitation;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.InvitationRepository;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Subscriptions. The plan decides which modules an organisation may use (others are locked, their data kept), how
 * many members it may have (guests don't count) and how much it may store. New organisations get a trial of Standard;
 * when it ends without a paid plan chosen they move to Free. Owners and members with the billing role change the plan;
 * the platform's super admins set anything (contracts, complimentary plans, longer trials).
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService implements ModuleEntitlements, StorageQuota {

    public static final String TRIAL_PLAN = "STANDARD";
    static final long GB = 1024L * 1024 * 1024;

    private final OrgAccess access;
    private final Plans plans;
    private final Subscriptions subscriptions;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final ActivityService activity;
    private final Clock clock;

    // ── What the plan allows ─────────────────────────────────────────────────────────

    /** The plan in force: the subscription's, or Free once cancelled (or without one). */
    @Transactional(readOnly = true)
    public Plan planOf(UUID orgId) {
        return subscriptions.findById(orgId).filter(s -> !Subscription.CANCELLED.equals(s.getStatus()))
                .flatMap(s -> plans.findById(s.getPlanCode())).orElseGet(() -> plans.findById(Plan.FREE).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean allows(UUID orgId, String moduleKey) {
        return planOf(orgId).getModules().contains(moduleKey);
    }

    /** Refuses adding {@code more} members (not guests) beyond the plan's limit; pending invitations count. */
    @Transactional(readOnly = true)
    public void requireSeats(UUID orgId, int more) {
        Integer max = planOf(orgId).getMaxMembers();
        if (max != null && seatsTaken(orgId) + more > max) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Your plan's member limit is reached. Upgrade to add more people", List.of());
        }
    }

    /** Refuses storing {@code bytes} more than the plan's storage. */
    @Override
    @Transactional(readOnly = true)
    public void require(UUID orgId, long bytes) {
        long limit = planOf(orgId).getStorageGb() * GB;
        if (subscriptions.storageUsed(orgId) + bytes > limit) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Your plan's file storage is full. Upgrade or delete files", List.of());
        }
    }

    /** Active members who aren't guests, plus pending invitations for non-guests. */
    long seatsTaken(UUID orgId) {
        long members = memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).count();
        long invited = invitations.findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(orgId).stream()
                .filter(i -> i.getRole() != OrgRole.GUEST && i.getExpiresAt().isAfter(clock.instant())).map(Invitation::getEmail).distinct().count();
        return members + invited;
    }

    // ── The organisation's side ──────────────────────────────────────────────────────

    /** A new organisation's trial of Standard. */
    @Transactional
    public void startTrial(UUID orgId, UUID by) {
        Plan p = plans.findById(TRIAL_PLAN).orElseGet(() -> plans.findById(Plan.FREE).orElseThrow());
        Subscription s = new Subscription();
        s.setOrgId(orgId);
        s.setPlanCode(p.getCode());
        Instant now = clock.instant();
        if (p.getTrialDays() > 0) {
            s.setStatus(Subscription.TRIALING);
            s.setTrialEndsAt(now.plus(Duration.ofDays(p.getTrialDays())));
        } else {
            s.setStatus(Subscription.ACTIVE);
        }
        s.setUpdatedBy(by);
        s.setCreatedAt(now);
        s.setUpdatedAt(now);
        subscriptions.save(s);
    }

    /** Admins see it; Owners and the billing role change it. */
    @Transactional(readOnly = true)
    public SubscriptionView view(String slug) {
        OrgContext ctx = access.require(slug);
        if (!ctx.isAdmin() && !canManage(ctx)) {
            throw BusinessException.forbidden();
        }
        return view(ctx);
    }

    /**
     * Choosing a plan: Free at once; a paid plan starts a new period now (billed per member). Downgrading below the
     * current members is refused; modules the new plan leaves out are locked. Complimentary plans are the platform's.
     */
    @Transactional
    public SubscriptionView change(String slug, ChangeRequest in) {
        OrgContext ctx = billing(slug);
        Plan p = plans.findById(in.planCode()).filter(Plan::isPublicPlan)
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "planCode", "Choose one of the plans"));
        Subscription s = subscription(ctx.orgId());
        if (Subscription.COMPLIMENTARY.equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Your plan is arranged with the platform team: contact them to change it", List.of());
        }
        if (p.getMaxMembers() != null && seatsTaken(ctx.orgId()) > p.getMaxMembers()) {
            throw BusinessException.field(ErrorCode.INVALID_STATE, "planCode", "You have more members than this plan allows. Remove some first");
        }
        String before = s.getPlanCode();
        Instant now = clock.instant();
        s.setPlanCode(p.getCode());
        s.setBillingCycle(in.cycle());
        s.setCancelAtPeriodEnd(false);
        s.setTrialEndsAt(null);
        if (paid(p)) {
            s.setStatus(Subscription.ACTIVE);
            s.setCurrentPeriodEnd(periodEnd(now, in.cycle()));
        } else {
            s.setStatus(Subscription.ACTIVE);
            s.setCurrentPeriodEnd(null);
        }
        s.setUpdatedBy(ctx.userId());
        s.setUpdatedAt(now);
        activity.record(ctx.orgId(), "org", "organisation", ctx.orgId(), ctx.userId(), "org.plan.changed",
                Map.of("from", before, "to", p.getCode(), "cycle", in.cycle()));
        return view(ctx);
    }

    /** A paid plan ends with the period (then Free); a trial ends at once. */
    @Transactional
    public SubscriptionView cancel(String slug) {
        OrgContext ctx = billing(slug);
        Subscription s = subscription(ctx.orgId());
        Instant now = clock.instant();
        if (Subscription.TRIALING.equals(s.getStatus())) {
            toFree(s, now);
        } else if (Subscription.ACTIVE.equals(s.getStatus()) && paid(plans.findById(s.getPlanCode()).orElseThrow())) {
            s.setCancelAtPeriodEnd(true);
        } else {
            throw new BusinessException(ErrorCode.INVALID_STATE, "There is no paid plan to cancel", List.of());
        }
        s.setUpdatedBy(ctx.userId());
        s.setUpdatedAt(now);
        activity.record(ctx.orgId(), "org", "organisation", ctx.orgId(), ctx.userId(), "org.plan.cancelled", Map.of("plan", s.getPlanCode()));
        return view(ctx);
    }

    // ── Over time ────────────────────────────────────────────────────────────────────

    /** Trials that ended and paid periods cancelled at their end move to Free. Returns how many moved. */
    @Transactional
    public int expire() {
        Instant now = clock.instant();
        int n = 0;
        for (Subscription s : subscriptions.findByStatus(Subscription.TRIALING)) {
            if (s.getTrialEndsAt() != null && !s.getTrialEndsAt().isAfter(now)) {
                toFree(s, now);
                n++;
            }
        }
        for (Subscription s : subscriptions.findByStatus(Subscription.ACTIVE)) {
            if (s.isCancelAtPeriodEnd() && s.getCurrentPeriodEnd() != null && !s.getCurrentPeriodEnd().isAfter(now)) {
                toFree(s, now);
                n++;
            }
        }
        return n;
    }

    // ── The platform's side ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanView> allPlans() {
        return plans.findAllByOrderBySortOrder().stream().map(SubscriptionService::planView).toList();
    }

    @Transactional(readOnly = true)
    public List<PlanView> publicPlans() {
        return plans.findAllByOrderBySortOrder().stream().filter(Plan::isPublicPlan).map(SubscriptionService::planView).toList();
    }

    /** Super admins (checked by the caller): creates or changes a plan. Existing subscriptions follow its new terms. */
    @Transactional
    public PlanView savePlan(String code, PlanRequest in, List<String> knownModules) {
        if (!code.matches("^[A-Z][A-Z0-9_]{1,19}$")) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "code", "Plan codes are capital letters, digits or _");
        }
        if (in.modules().stream().anyMatch(m -> !knownModules.contains(m))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "modules", "Unknown module");
        }
        Plan p = plans.findById(code).orElseGet(() -> {
            Plan n = new Plan();
            n.setCode(code);
            return n;
        });
        p.setNameEn(in.nameEn().strip());
        p.setNameKm(in.nameKm().strip());
        p.setPriceMonthly(in.priceMonthly());
        p.setPriceYearly(in.priceYearly());
        p.setMaxMembers(in.maxMembers());
        p.setStorageGb(in.storageGb());
        p.setModules(in.modules().stream().distinct().toList());
        p.setTrialDays((short) in.trialDays());
        p.setPublicPlan(in.publicPlan());
        p.setSortOrder((short) in.sortOrder());
        p.setUpdatedAt(clock.instant());
        return planView(plans.save(p));
    }

    /** Super admins (checked by the caller): sets an organisation's subscription as agreed with them. */
    @Transactional
    public SubscriptionBrief set(UUID orgId, AdminSubscriptionRequest in, UUID by) {
        plans.findById(in.planCode()).orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "planCode", "Unknown plan"));
        Subscription s = subscriptions.findById(orgId).orElseGet(() -> {
            Subscription n = new Subscription();
            n.setOrgId(orgId);
            n.setCreatedAt(clock.instant());
            return n;
        });
        s.setPlanCode(in.planCode());
        s.setStatus(in.status());
        s.setBillingCycle(in.cycle());
        s.setTrialEndsAt(Subscription.TRIALING.equals(in.status()) ? in.trialEndsAt() : null);
        s.setCurrentPeriodEnd(in.currentPeriodEnd());
        s.setCancelAtPeriodEnd(false);
        s.setNote(in.note() == null || in.note().isBlank() ? null : in.note().strip());
        s.setUpdatedBy(by);
        s.setUpdatedAt(clock.instant());
        subscriptions.save(s);
        return brief(s);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionBrief> brief(UUID orgId) {
        return subscriptions.findById(orgId).map(SubscriptionService::brief);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    /** Owners, and members whose custom role includes billing (like GitHub's billing manager); not every Admin. */
    private OrgContext billing(String slug) {
        OrgContext ctx = access.require(slug);
        if (!canManage(ctx)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private static boolean canManage(OrgContext ctx) {
        return ctx.role() == OrgRole.OWNER || ctx.grants().orgWide().contains(RoleCatalogue.BILLING);
    }

    private SubscriptionView view(OrgContext ctx) {
        Subscription s = subscription(ctx.orgId());
        Plan p = planOf(ctx.orgId());
        long seats = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).count();
        BigDecimal price = "YEARLY".equals(s.getBillingCycle()) ? p.getPriceYearly() : p.getPriceMonthly();
        BigDecimal next = Subscription.COMPLIMENTARY.equals(s.getStatus()) ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(seats));
        return new SubscriptionView(planView(p), s.getStatus(), s.getBillingCycle(), s.getTrialEndsAt(), s.getCurrentPeriodEnd(),
                s.isCancelAtPeriodEnd(), new Usage(seats, p.getMaxMembers(), subscriptions.storageUsed(ctx.orgId()), p.getStorageGb() * GB),
                next, publicPlans(), canManage(ctx));
    }

    private Subscription subscription(UUID orgId) {
        return subscriptions.findById(orgId).orElseGet(() -> {
            startTrial(orgId, null);
            return subscriptions.findById(orgId).orElseThrow();
        });
    }

    private void toFree(Subscription s, Instant now) {
        s.setPlanCode(Plan.FREE);
        s.setStatus(Subscription.ACTIVE);
        s.setTrialEndsAt(null);
        s.setCurrentPeriodEnd(null);
        s.setCancelAtPeriodEnd(false);
        s.setUpdatedAt(now);
        activity.record(s.getOrgId(), "org", "organisation", s.getOrgId(), null, "org.plan.changed", Map.of("to", Plan.FREE));
    }

    private static boolean paid(Plan p) {
        return p.getPriceMonthly().signum() > 0 || p.getPriceYearly().signum() > 0;
    }

    static Instant periodEnd(Instant from, String cycle) {
        var d = from.atOffset(ZoneOffset.UTC);
        return ("YEARLY".equals(cycle) ? d.plusYears(1) : d.plusMonths(1)).toInstant();
    }

    static PlanView planView(Plan p) {
        return new PlanView(p.getCode(), p.getNameEn(), p.getNameKm(), p.getPriceMonthly(), p.getPriceYearly(), p.getMaxMembers(),
                p.getStorageGb(), p.getModules(), p.getTrialDays(), p.isPublicPlan(), p.getSortOrder());
    }

    private static SubscriptionBrief brief(Subscription s) {
        return new SubscriptionBrief(s.getOrgId(), s.getPlanCode(), s.getStatus(), s.getBillingCycle(), s.getTrialEndsAt(),
                s.getCurrentPeriodEnd(), s.getNote());
    }
}
