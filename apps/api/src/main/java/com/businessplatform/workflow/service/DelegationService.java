package com.businessplatform.workflow.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;
import com.businessplatform.workflow.domain.ApprovalDelegation;
import com.businessplatform.workflow.payload.WorkflowPayloads.*;
import com.businessplatform.workflow.repository.WorkflowRepositories.Delegations;

import lombok.RequiredArgsConstructor;

/**
 * Acting approvers: while someone is away (a business trip, leave), their delegate decides their approval steps on
 * their behalf, from the first to the last day (dates in Asia/Phnom_Penh). People set their own; Admins and HR managers
 * set them for anyone. One hop only: a delegate's own delegate doesn't act for the first person.
 */
@Service
@RequiredArgsConstructor
public class DelegationService {

    static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final OrgAccess access;
    private final Delegations delegations;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    @Transactional(readOnly = true)
    public DelegationsView list(String slug) {
        OrgContext ctx = member(slug);
        LocalDate today = today();
        List<ApprovalDelegation> current = delegations.findByOrgIdAndRevokedAtIsNullOrderByStartsOn(ctx.orgId()).stream()
                .filter(d -> !d.getEndsOn().isBefore(today)).toList();
        List<ApprovalDelegation> mine = current.stream()
                .filter(d -> d.getUserId().equals(ctx.userId()) || d.getDelegateId().equals(ctx.userId())).toList();
        boolean manage = manages(ctx);
        Map<UUID, PersonView> ps = persons(current.stream().flatMap(d -> Stream.of(d.getUserId(), d.getDelegateId())).toList());
        return new DelegationsView(views(ctx, mine, ps, today), manage ? views(ctx, current, ps, today) : List.of(), manage);
    }

    @Transactional
    public DelegationView create(String slug, DelegationRequest in) {
        OrgContext ctx = member(slug);
        UUID user = in.userId() == null ? ctx.userId() : in.userId();
        if (!user.equals(ctx.userId()) && !manages(ctx)) {
            throw BusinessException.forbidden();
        }
        if (!worker(ctx, user)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Choose a member of the organisation");
        }
        if (user.equals(in.delegateId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "delegateId", "Choose someone else to approve for them");
        }
        if (!worker(ctx, in.delegateId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "delegateId", "Choose a member of the organisation");
        }
        if (in.endsOn().isBefore(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "The end is before the start");
        }
        if (in.endsOn().isBefore(today())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "This period is over already");
        }
        if (ChronoUnit.DAYS.between(in.startsOn(), in.endsOn()) > 400) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "At most about a year at a time");
        }
        boolean overlaps = delegations.findByOrgIdAndUserIdAndRevokedAtIsNull(ctx.orgId(), user).stream()
                .anyMatch(d -> !d.getEndsOn().isBefore(in.startsOn()) && !d.getStartsOn().isAfter(in.endsOn()));
        if (overlaps) {
            throw BusinessException.field(ErrorCode.CONFLICT, "startsOn", "There is already an acting approver for these dates");
        }
        ApprovalDelegation d = new ApprovalDelegation();
        d.setId(Ids.next());
        d.setOrgId(ctx.orgId());
        d.setUserId(user);
        d.setDelegateId(in.delegateId());
        d.setStartsOn(in.startsOn());
        d.setEndsOn(in.endsOn());
        d.setReason(in.reason() == null || in.reason().isBlank() ? null : in.reason().strip());
        d.setCreatedBy(ctx.userId());
        d.setCreatedAt(clock.instant());
        delegations.save(d);
        Map<UUID, PersonView> ps = persons(List.of(user, in.delegateId()));
        Map<String, Object> data = Map.of("name", ps.get(user).name(), "delegate", ps.get(in.delegateId()).name(),
                "from", d.getStartsOn().toString(), "to", d.getEndsOn().toString());
        activity.record(ctx.orgId(), "approvals", "delegation", d.getId(), ctx.userId(), "approval.delegated", data);
        notifications.send(ctx.orgId(), List.of(in.delegateId()), ctx.userId(), "approval.delegated",
                Text.of("You approve for {name} from {from} to {to}", data), Text.raw(d.getReason()), "/" + ctx.slug() + "/approvals");
        return views(ctx, List.of(d), ps, today()).get(0);
    }

    /** Ends it now (the person, whoever set it, Admins and HR managers). */
    @Transactional
    public void revoke(String slug, UUID id) {
        OrgContext ctx = member(slug);
        ApprovalDelegation d = delegations.findByIdAndOrgId(id, ctx.orgId()).filter(x -> x.getRevokedAt() == null)
                .orElseThrow(BusinessException::notFound);
        if (!mayRevoke(ctx, d)) {
            throw BusinessException.forbidden();
        }
        d.setRevokedAt(clock.instant());
        Map<UUID, PersonView> ps = persons(List.of(d.getUserId(), d.getDelegateId()));
        activity.record(ctx.orgId(), "approvals", "delegation", d.getId(), ctx.userId(), "approval.delegation.ended",
                Map.of("name", ps.get(d.getUserId()).name(), "delegate", ps.get(d.getDelegateId()).name()));
    }

    // ── For the approval engine ──────────────────────────────────────────────────────

    /** Who acts for {@code approverId} today, if anyone. */
    @Transactional(readOnly = true)
    public Optional<UUID> actingFor(UUID orgId, UUID approverId) {
        LocalDate today = today();
        return delegations.findByOrgIdAndUserIdAndRevokedAtIsNull(orgId, approverId).stream().filter(d -> d.covers(today))
                .map(ApprovalDelegation::getDelegateId).findFirst();
    }

    /** Whom {@code delegateId} acts for today. */
    @Transactional(readOnly = true)
    public List<UUID> actingAs(UUID orgId, UUID delegateId) {
        LocalDate today = today();
        return delegations.findByOrgIdAndDelegateIdAndRevokedAtIsNull(orgId, delegateId).stream().filter(d -> d.covers(today))
                .map(ApprovalDelegation::getUserId).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private OrgContext member(String slug) {
        OrgContext ctx = access.require(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private static boolean manages(OrgContext ctx) {
        return ctx.can(RoleCatalogue.HR);
    }

    private static boolean mayRevoke(OrgContext ctx, ApprovalDelegation d) {
        return d.getUserId().equals(ctx.userId()) || ctx.userId().equals(d.getCreatedBy()) || manages(ctx);
    }

    private boolean worker(OrgContext ctx, UUID userId) {
        return userId != null && memberships.findByOrgIdAndUserId(ctx.orgId(), userId)
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).isPresent();
    }

    private List<DelegationView> views(OrgContext ctx, List<ApprovalDelegation> ds, Map<UUID, PersonView> ps, LocalDate today) {
        return ds.stream().sorted(Comparator.comparing(ApprovalDelegation::getStartsOn))
                .map(d -> new DelegationView(d.getId(), ps.get(d.getUserId()), ps.get(d.getDelegateId()), d.getStartsOn(),
                        d.getEndsOn(), d.getReason(), d.covers(today), mayRevoke(ctx, d))).toList();
    }

    private Map<UUID, PersonView> persons(List<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        return wanted.isEmpty() ? Map.of() : users.findByIdIn(wanted).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> new PersonView(u.getId(), u.getName()), (a, b) -> a, java.util.HashMap::new));
    }
}
