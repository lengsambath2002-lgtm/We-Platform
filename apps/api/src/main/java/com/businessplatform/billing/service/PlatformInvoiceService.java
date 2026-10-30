package com.businessplatform.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.billing.domain.PlatformInvoice;
import com.businessplatform.billing.domain.Plan;
import com.businessplatform.billing.domain.Subscription;
import com.businessplatform.billing.khqr.KhqrBuilder;
import com.businessplatform.billing.payload.BillingPayloads.*;
import com.businessplatform.billing.repository.BillingRepositories.Invoices;
import com.businessplatform.billing.repository.BillingRepositories.Plans;
import com.businessplatform.billing.repository.BillingRepositories.Subscriptions;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.mail.MailService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The platform's invoices to organisations. A paid plan is invoiced at the start of each period (price per member ×
 * members who aren't guests), due after {@code platform.billing.due-days}, payable by bank transfer or KHQR. The
 * organisation says it paid (with the bank's reference) and the platform confirms it; past the due date the
 * subscription is PAST_DUE, and past the grace period it moves to Free (the invoice is void).
 */
@Service
@RequiredArgsConstructor
public class PlatformInvoiceService {

    static final ZoneId PP = ZoneId.of("Asia/Phnom_Penh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(PP);

    private final OrgAccess access;
    private final Invoices invoices;
    private final Plans plans;
    private final Subscriptions subscriptions;
    private final MembershipRepository memberships;
    private final OrganisationRepository orgs;
    private final UserRepository users;
    private final MailService mail;
    private final ActivityService activity;
    private final BillingProperties billing;
    private final Clock clock;
    @Value("${platform.app-url:http://localhost:3300}")
    private String appUrl;

    // ── Issuing ──────────────────────────────────────────────────────────────────────

    /** Invoices the period [start, end) of a paid plan; earlier open invoices of the organisation are voided. */
    @Transactional
    public PlatformInvoice issue(UUID orgId, Plan p, String cycle, Instant start, Instant end, String replacing) {
        invoices.findByOrgIdAndStatus(orgId, PlatformInvoice.OPEN).forEach(i -> {
            i.setStatus(PlatformInvoice.VOID);
            i.setVoidReason(replacing);
        });
        int seats = (int) memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).count();
        BigDecimal unit = "YEARLY".equals(cycle) ? p.getPriceYearly() : p.getPriceMonthly();
        Instant now = clock.instant();
        PlatformInvoice i = new PlatformInvoice();
        i.setId(Ids.next());
        i.setOrgId(orgId);
        i.setNumber("INV-" + now.atZone(PP).getYear() + "-" + String.format("%05d", invoices.nextNumber()));
        i.setPlanCode(p.getCode());
        i.setBillingCycle(cycle);
        i.setSeats(Math.max(seats, 1));
        i.setUnitPrice(unit);
        i.setAmount(unit.multiply(BigDecimal.valueOf(i.getSeats())).setScale(2, RoundingMode.HALF_UP));
        i.setPeriodStart(start);
        i.setPeriodEnd(end);
        i.setStatus(PlatformInvoice.OPEN);
        i.setIssuedAt(now);
        i.setDueAt(now.plus(Duration.ofDays(billing.due())));
        i.setCreatedAt(now);
        if (billing.khqr() && i.getAmount().signum() > 0) {
            i.setKhqr(KhqrBuilder.build(new KhqrBuilder.Request(billing.bakongAccountId(), billing.sellerName(), billing.merchantCity(),
                    KhqrBuilder.USD, i.getAmount(), i.getNumber(), null, null, now, i.getDueAt())));
        }
        invoices.save(i);
        Organisation o = orgs.findById(orgId).orElseThrow();
        activity.record(orgId, "org", "organisation", orgId, null, "org.invoice.issued", Map.of("number", i.getNumber(), "amount", i.getAmount()));
        Map<String, Object> args = Map.of("number", i.getNumber(), "amount", "$" + i.getAmount().toPlainString(), "org", o.getName(),
                "due", DAY.format(i.getDueAt()));
        for (AppUser u : payers(orgId)) {
            mail.queue(mail.letter(u.getEmail(), u.getLocale(), billing.sellerName(), Text.of("Invoice {number} for {org}: {amount}", args),
                    List.of(Text.of("Your Business Platform invoice {number} is {amount}, due {due}.", args),
                            Text.of("Pay by bank transfer or KHQR, then tell us the transfer reference on the invoice.")),
                    Text.of("View the invoice"), appUrl + "/" + o.getSlug() + "/admin/billing/invoices/" + i.getId(), null));
        }
        return i;
    }

    /** Voids the organisation's open invoices (a move to a free plan). */
    @Transactional
    public void voidOpen(UUID orgId, String reason) {
        invoices.findByOrgIdAndStatus(orgId, PlatformInvoice.OPEN).forEach(i -> {
            i.setStatus(PlatformInvoice.VOID);
            i.setVoidReason(reason);
        });
    }

    // ── The organisation's side ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceView> list(String slug) {
        OrgContext ctx = viewer(slug);
        return views(invoices.findByOrgIdOrderByIssuedAtDesc(ctx.orgId()));
    }

    @Transactional(readOnly = true)
    public InvoiceView get(String slug, UUID id) {
        OrgContext ctx = viewer(slug);
        return views(List.of(invoices.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound))).get(0);
    }

    /** "We paid": the transfer reference; the platform's team is told and confirms it. */
    @Transactional
    public InvoiceView claim(String slug, UUID id, ClaimRequest in) {
        OrgContext ctx = access.require(slug);
        if (!SubscriptionService.canManage(ctx)) {
            throw BusinessException.forbidden();
        }
        PlatformInvoice i = invoices.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!PlatformInvoice.OPEN.equals(i.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is not waiting for payment", List.of());
        }
        i.setClaimedAt(clock.instant());
        i.setClaimReference(in.reference().strip());
        i.setClaimedBy(ctx.userId());
        activity.record(ctx.orgId(), "org", "organisation", ctx.orgId(), ctx.userId(), "org.invoice.claimed", Map.of("number", i.getNumber()));
        Map<String, Object> args = Map.of("number", i.getNumber(), "org", ctx.orgName(), "reference", i.getClaimReference(),
                "amount", "$" + i.getAmount().toPlainString());
        users.findAll().stream().filter(AppUser::isSuperAdmin).forEach(u -> mail.queue(mail.letter(u.getEmail(), u.getLocale(), null,
                Text.of("{org} paid invoice {number}", args), List.of(Text.of("{org} says it paid {amount} for invoice {number} (reference {reference}). Check the bank and confirm it.", args)),
                Text.of("Open the invoices"), appUrl + "/admin/invoices", null)));
        return views(List.of(i)).get(0);
    }

    // ── The platform's side (super admins: checked by the caller) ────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceView> all(String status) {
        List<PlatformInvoice> found = status == null || status.isBlank() ? invoices.findTop200ByOrderByIssuedAtDesc()
                : invoices.findByStatusOrderByDueAt(status);
        return views(found);
    }

    /** A payment received: the invoice is paid; a late subscription is active again; the payers get a receipt. */
    @Transactional
    public InvoiceView record(UUID id, PaymentRequest in, UUID by) {
        PlatformInvoice i = invoices.findById(id).orElseThrow(BusinessException::notFound);
        if (!PlatformInvoice.OPEN.equals(i.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is not waiting for payment", List.of());
        }
        Instant now = clock.instant();
        i.setStatus(PlatformInvoice.PAID);
        i.setPaidAt(now);
        i.setPaymentMethod(in.method());
        i.setPaymentReference(in.reference() == null || in.reference().isBlank() ? i.getClaimReference() : in.reference().strip());
        i.setRecordedBy(by);
        subscriptions.findById(i.getOrgId()).filter(s -> Subscription.PAST_DUE.equals(s.getStatus())).ifPresent(s -> {
            s.setStatus(Subscription.ACTIVE);
            s.setUpdatedAt(now);
        });
        activity.record(i.getOrgId(), "org", "organisation", i.getOrgId(), by, "org.invoice.paid", Map.of("number", i.getNumber()));
        Organisation o = orgs.findById(i.getOrgId()).orElseThrow();
        Map<String, Object> args = Map.of("number", i.getNumber(), "amount", "$" + i.getAmount().toPlainString(), "org", o.getName());
        for (AppUser u : payers(i.getOrgId())) {
            mail.queue(mail.letter(u.getEmail(), u.getLocale(), billing.sellerName(), Text.of("Payment received for invoice {number}", args),
                    List.of(Text.of("Thank you: we received {amount} for invoice {number} ({org}).", args)),
                    Text.of("View the invoice"), appUrl + "/" + o.getSlug() + "/admin/billing/invoices/" + i.getId(), null));
        }
        return views(List.of(i)).get(0);
    }

    @Transactional
    public InvoiceView voidInvoice(UUID id, VoidRequest in, UUID by) {
        PlatformInvoice i = invoices.findById(id).orElseThrow(BusinessException::notFound);
        if (!PlatformInvoice.OPEN.equals(i.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is not waiting for payment", List.of());
        }
        i.setStatus(PlatformInvoice.VOID);
        i.setVoidReason(in.reason().strip());
        activity.record(i.getOrgId(), "org", "organisation", i.getOrgId(), by, "org.invoice.voided", Map.of("number", i.getNumber()));
        return views(List.of(i)).get(0);
    }

    // ── Over time ────────────────────────────────────────────────────────────────────

    /**
     * Daily: paid plans whose period ended are renewed (a new invoice) unless cancelled; open invoices past due make
     * the subscription PAST_DUE; past the grace period the organisation moves to Free and the invoice is void.
     * Returns {renewed, late, downgraded}.
     */
    @Transactional
    public int[] run() {
        Instant now = clock.instant();
        int renewed = 0;
        int late = 0;
        int downgraded = 0;
        for (Subscription s : subscriptions.findByStatus(Subscription.ACTIVE)) {
            Plan p = plans.findById(s.getPlanCode()).orElse(null);
            if (p == null || !SubscriptionService.paid(p) || s.isCancelAtPeriodEnd() || s.getCurrentPeriodEnd() == null
                    || s.getCurrentPeriodEnd().isAfter(now)) {
                continue;
            }
            Instant end = SubscriptionService.periodEnd(s.getCurrentPeriodEnd(), s.getBillingCycle());
            issue(s.getOrgId(), p, s.getBillingCycle(), s.getCurrentPeriodEnd(), end, "Replaced by the next period's invoice");
            s.setCurrentPeriodEnd(end);
            s.setUpdatedAt(now);
            renewed++;
        }
        for (PlatformInvoice i : invoices.findByStatusOrderByDueAt(PlatformInvoice.OPEN)) {
            if (i.getDueAt().isAfter(now)) {
                continue;
            }
            Subscription s = subscriptions.findById(i.getOrgId()).orElse(null);
            if (s == null || Subscription.COMPLIMENTARY.equals(s.getStatus())) {
                continue;
            }
            if (i.getDueAt().plus(Duration.ofDays(billing.grace())).isAfter(now)) {
                if (Subscription.ACTIVE.equals(s.getStatus())) {
                    s.setStatus(Subscription.PAST_DUE);
                    s.setUpdatedAt(now);
                    late++;
                }
            } else {
                i.setStatus(PlatformInvoice.VOID);
                i.setVoidReason("Not paid: moved to Free");
                s.setPlanCode(Plan.FREE);
                s.setStatus(Subscription.ACTIVE);
                s.setCurrentPeriodEnd(null);
                s.setCancelAtPeriodEnd(false);
                s.setUpdatedAt(now);
                activity.record(s.getOrgId(), "org", "organisation", s.getOrgId(), null, "org.plan.changed", Map.of("to", Plan.FREE));
                downgraded++;
            }
        }
        return new int[] { renewed, late, downgraded };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private OrgContext viewer(String slug) {
        OrgContext ctx = access.require(slug);
        if (!ctx.isAdmin() && !SubscriptionService.canManage(ctx)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    /** Who is billed: the active Owners. */
    private List<AppUser> payers(UUID orgId) {
        List<UUID> ids = memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() == OrgRole.OWNER).map(Membership::getUserId).toList();
        return users.findByIdIn(ids).stream().filter(u -> u.getDisabledAt() == null).toList();
    }

    PayTo payTo() {
        return new PayTo(billing.sellerName(), billing.sellerAddress(), blank(billing.sellerTaxId()), blank(billing.bankName()),
                blank(billing.bankAccountName()), blank(billing.bankAccountNumber()), billing.khqr());
    }

    private List<InvoiceView> views(List<PlatformInvoice> found) {
        Map<UUID, String> names = orgs.findAllById(found.stream().map(PlatformInvoice::getOrgId).distinct().toList()).stream()
                .collect(Collectors.toMap(Organisation::getId, Organisation::getName, (a, b) -> a));
        Instant now = clock.instant();
        PayTo payTo = payTo();
        return found.stream().map(i -> new InvoiceView(i.getId(), i.getOrgId(), names.get(i.getOrgId()), i.getNumber(), i.getPlanCode(),
                i.getBillingCycle(), i.getSeats(), i.getUnitPrice(), i.getAmount(), i.getPeriodStart(), i.getPeriodEnd(), i.getStatus(),
                i.getIssuedAt(), i.getDueAt(), PlatformInvoice.OPEN.equals(i.getStatus()) && i.getDueAt().isBefore(now), i.getKhqr(),
                i.getClaimedAt(), i.getClaimReference(), i.getPaidAt(), i.getPaymentMethod(), i.getPaymentReference(), i.getVoidReason(),
                payTo)).toList();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
