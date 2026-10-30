package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.Offboarding;
import com.businessplatform.hr.domain.OffboardingItem;
import com.businessplatform.hr.domain.OffboardingTemplate;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.payload.OffboardingPayloads.*;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.OffboardingRepositories.Items;
import com.businessplatform.hr.repository.OffboardingRepositories.Offboardings;
import com.businessplatform.hr.repository.OffboardingRepositories.Templates;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.OrgService;
import com.businessplatform.org.service.OrgStructureService;
import com.businessplatform.org.service.RoleCatalogue;
import com.businessplatform.workflow.service.ApprovalService;

import lombok.RequiredArgsConstructor;

/**
 * Offboarding. People resign themselves (REQUESTED; their manager and HR are told); HR accepts it or starts a departure
 * directly (IN_PROGRESS), working through a checklist from the organisation's template while handing over reports,
 * approvals and units; on or after the last day, with the checklist done, HR completes it: the person leaves (their
 * access ends, the profile keeps the date). HR managers of a unit handle its people; nobody offboards themselves.
 */
@Service
@RequiredArgsConstructor
public class OffboardingService {

    static final String RECORD = "offboarding";

    private final HrAccess hr;
    private final ProfileService profiles;
    private final EmployeeProfileRepository profileRows;
    private final BalanceService balances;
    private final Offboardings offboardings;
    private final Items items;
    private final Templates templates;
    private final MembershipRepository memberships;
    private final OrgService orgs;
    private final OrgStructureService structure;
    private final ApprovalService approvals;
    private final NotificationService notifications;
    private final ActivityService activity;
    private final Clock clock;

    // ── Resigning, starting, changing ────────────────────────────────────────────────

    @Transactional
    public OffboardingDetail resign(String slug, ResignRequest in) {
        OrgContext ctx = hr.require(slug);
        profiles.profile(ctx, ctx.userId());
        noneOpen(ctx, ctx.userId());
        LocalDate today = LeaveService.today(clock);
        if (in.lastDay().isBefore(today)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lastDay", "The last day can't be in the past");
        }
        Offboarding o = create(ctx, ctx.userId(), "RESIGNATION", today, in.lastDay(), in.reason(), Offboarding.REQUESTED);
        o.setRequestedBy(ctx.userId());
        String name = name(ctx.userId());
        Map<String, Object> data = Map.of("name", name, "lastDay", in.lastDay().toString());
        audit(ctx, o, "offboarding.resigned", data);
        // Their manager and the organisation's HR managers (Owners and Admins) hear about it.
        List<UUID> to = new ArrayList<>(admins(ctx));
        profiles.managerOf(ctx.orgId(), ctx.userId()).ifPresent(to::add);
        notifications.send(ctx.orgId(), to, ctx.userId(), "offboarding.resigned",
                Text.of("{name} resigned; proposed last day {lastDay}", data), Text.raw(o.getReason()), link(ctx, o));
        return detail(ctx, o);
    }

    @Transactional
    public OffboardingDetail start(String slug, StartRequest in) {
        OrgContext ctx = hr.require(slug);
        requireFor(ctx, in.userId());
        profiles.profile(ctx, in.userId());
        noneOpen(ctx, in.userId());
        LocalDate notice = in.noticeOn() == null ? LeaveService.today(clock) : in.noticeOn();
        if (in.lastDay().isBefore(notice)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lastDay", "The last day is before the notice");
        }
        Offboarding o = create(ctx, in.userId(), in.kind(), notice, in.lastDay(), in.reason(), Offboarding.IN_PROGRESS);
        o.setRehire(in.rehire());
        o.setStartedBy(ctx.userId());
        checklist(ctx, o);
        audit(ctx, o, "offboarding.started", Map.of("name", name(in.userId()), "kind", in.kind(), "lastDay", in.lastDay().toString()));
        return detail(ctx, o);
    }

    /** HR accepts a resignation (maybe with another last day): the checklist starts. */
    @Transactional
    public OffboardingDetail accept(String slug, UUID id, UpdateRequest in) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = find(ctx, id);
        requireFor(ctx, o.getUserId());
        if (!Offboarding.REQUESTED.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This resignation was handled already", List.of());
        }
        apply(o, in);
        o.setStatus(Offboarding.IN_PROGRESS);
        o.setStartedBy(ctx.userId());
        checklist(ctx, o);
        audit(ctx, o, "offboarding.accepted", Map.of("name", name(o.getUserId()), "lastDay", o.getLastDay().toString()));
        notifications.send(ctx.orgId(), List.of(o.getUserId()), ctx.userId(), "offboarding.accepted",
                Text.of("Your resignation was accepted; last day {lastDay}", Map.of("lastDay", o.getLastDay().toString())),
                Text.raw(null), link(ctx, o));
        return detail(ctx, o);
    }

    @Transactional
    public OffboardingDetail update(String slug, UUID id, UpdateRequest in) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = open(ctx, id);
        requireFor(ctx, o.getUserId());
        apply(o, in);
        return detail(ctx, o);
    }

    /** HR, or the person while their resignation is only requested (they changed their mind). */
    @Transactional
    public OffboardingDetail cancel(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = open(ctx, id);
        if (!canCancel(ctx, o)) {
            throw BusinessException.forbidden();
        }
        o.setStatus(Offboarding.CANCELLED);
        o.setCancelledAt(clock.instant());
        o.setUpdatedAt(clock.instant());
        audit(ctx, o, "offboarding.cancelled", Map.of("name", name(o.getUserId())));
        return detail(ctx, o);
    }

    /** On or after the last day, with every step done: the person leaves and loses access. */
    @Transactional
    public OffboardingDetail complete(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = open(ctx, id);
        requireFor(ctx, o.getUserId());
        if (!Offboarding.IN_PROGRESS.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Accept the resignation first", List.of());
        }
        if (o.getLastDay().isAfter(LeaveService.today(clock))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Their last day hasn't come yet", List.of());
        }
        if (items.findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(o.getId()).stream().anyMatch(i -> i.getDoneAt() == null)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Finish the checklist first", List.of());
        }
        profileRows.findByOrgIdAndUserId(ctx.orgId(), o.getUserId()).ifPresent(p -> {
            p.setLeftOn(o.getLastDay());
            p.setUpdatedAt(clock.instant());
        });
        orgs.endMembership(ctx, o.getUserId());
        o.setStatus(Offboarding.COMPLETED);
        o.setCompletedBy(ctx.userId());
        o.setCompletedAt(clock.instant());
        o.setUpdatedAt(clock.instant());
        audit(ctx, o, "offboarding.completed", Map.of("name", name(o.getUserId()), "lastDay", o.getLastDay().toString()));
        return detail(ctx, o);
    }

    // ── The checklist ────────────────────────────────────────────────────────────────

    @Transactional
    public OffboardingDetail addItem(String slug, UUID id, ItemRequest in) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = open(ctx, id);
        requireFor(ctx, o.getUserId());
        int next = items.findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(o.getId()).size();
        item(ctx, o, in.title().strip(), in.dueDate(), next);
        return detail(ctx, o);
    }

    @Transactional
    public OffboardingDetail setDone(String slug, UUID itemId, boolean done) {
        OrgContext ctx = hr.require(slug);
        OffboardingItem i = items.findByIdAndOrgId(itemId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        Offboarding o = open(ctx, i.getOffboardingId());
        requireFor(ctx, o.getUserId());
        i.setDoneAt(done ? clock.instant() : null);
        i.setDoneBy(done ? ctx.userId() : null);
        return detail(ctx, o);
    }

    @Transactional
    public OffboardingDetail removeItem(String slug, UUID itemId) {
        OrgContext ctx = hr.require(slug);
        OffboardingItem i = items.findByIdAndOrgId(itemId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        Offboarding o = open(ctx, i.getOffboardingId());
        requireFor(ctx, o.getUserId());
        items.delete(i);
        items.flush();
        return detail(ctx, o);
    }

    // ── Reading ──────────────────────────────────────────────────────────────────────

    /** {@code open}: requested and in progress; otherwise completed and cancelled. Only people the caller manages. */
    @Transactional(readOnly = true)
    public List<OffboardingSummary> list(String slug, boolean open) {
        OrgContext ctx = hr.requireTeam(slug);
        Set<UUID> scope = ctx.scopeOf(RoleCatalogue.HR);
        List<Offboarding> found = offboardings.findByOrgIdOrderByLastDayDesc(ctx.orgId()).stream()
                .filter(o -> o.open() == open && (scope == null || scope.contains(o.getUserId()) || ctx.can(RoleCatalogue.HR))).toList();
        if (open) {
            found = found.stream().sorted(Comparator.comparing(Offboarding::getLastDay)).toList();
        }
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(Offboarding::getUserId).toList());
        return found.stream().map(o -> summary(o, people.get(o.getUserId()))).toList();
    }

    @Transactional(readOnly = true)
    public OffboardingDetail get(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Offboarding o = find(ctx, id);
        if (!o.getUserId().equals(ctx.userId()) && !HrAccess.managesPerson(ctx, o.getUserId())) {
            throw BusinessException.notFound();
        }
        return detail(ctx, o);
    }

    /** The caller's own open resignation or departure, if any. */
    @Transactional(readOnly = true)
    public java.util.Optional<OffboardingDetail> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        return offboardings.findByOrgIdAndUserIdOrderByCreatedAtDesc(ctx.orgId(), ctx.userId()).stream().filter(Offboarding::open)
                .findFirst().map(o -> detail(ctx, o));
    }

    // ── The template ─────────────────────────────────────────────────────────────────

    @Transactional
    public List<TemplateItem> template(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        return template(ctx).stream().map(t -> new TemplateItem(t.getTitle(), t.getDueDays())).toList();
    }

    @Transactional
    public List<TemplateItem> saveTemplate(String slug, TemplateRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        templates.deleteAll(template(ctx));
        templates.flush();
        short order = 0;
        for (TemplateItem i : in.items()) {
            OffboardingTemplate t = new OffboardingTemplate();
            t.setId(Ids.next());
            t.setOrgId(ctx.orgId());
            t.setTitle(i.title().strip());
            t.setDueDays((short) i.dueDays());
            t.setSortOrder(order++);
            templates.save(t);
        }
        return template(slug);
    }

    /** The organisation's steps, seeded the first time with what a Cambodian employer usually does. */
    private List<OffboardingTemplate> template(OrgContext ctx) {
        List<OffboardingTemplate> found = templates.findByOrgIdOrderBySortOrder(ctx.orgId());
        if (!found.isEmpty()) {
            return found;
        }
        Map<String, Integer> seed = new LinkedHashMap<>();
        seed.put("Hand over work, files and passwords", -3);
        seed.put("Exit interview", -2);
        seed.put("Return laptop, phone and other equipment", 0);
        seed.put("Return ID card, keys and access card", 0);
        seed.put("Remove email and system access", 0);
        seed.put("Final pay: salary to the last day, unused leave, severance if due", 0);
        seed.put("Give the certificate of employment", 0);
        seed.put("Report the departure to NSSF", 7);
        short order = 0;
        List<OffboardingTemplate> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : seed.entrySet()) {
            OffboardingTemplate t = new OffboardingTemplate();
            t.setId(Ids.next());
            t.setOrgId(ctx.orgId());
            t.setTitle(e.getKey());
            t.setDueDays(e.getValue().shortValue());
            t.setSortOrder(order++);
            out.add(templates.save(t));
        }
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private Offboarding create(OrgContext ctx, UUID userId, String kind, LocalDate notice, LocalDate lastDay, String reason, String status) {
        Offboarding o = new Offboarding();
        o.setId(Ids.next());
        o.setOrgId(ctx.orgId());
        o.setUserId(userId);
        o.setKind(kind);
        o.setStatus(status);
        o.setNoticeOn(notice);
        o.setLastDay(lastDay);
        o.setReason(ProfileService.clean(reason));
        o.setCreatedAt(clock.instant());
        o.setUpdatedAt(clock.instant());
        return offboardings.saveAndFlush(o);
    }

    private void apply(Offboarding o, UpdateRequest in) {
        if (in.lastDay().isBefore(o.getNoticeOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "lastDay", "The last day is before the notice");
        }
        o.setKind(in.kind());
        o.setLastDay(in.lastDay());
        o.setReason(ProfileService.clean(in.reason()));
        o.setRehire(in.rehire());
        o.setUpdatedAt(clock.instant());
    }

    private void checklist(OrgContext ctx, Offboarding o) {
        if (!items.findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(o.getId()).isEmpty()) {
            return;
        }
        int n = 0;
        for (OffboardingTemplate t : template(ctx)) {
            item(ctx, o, t.getTitle(), o.getLastDay().plusDays(t.getDueDays()), n++);
        }
    }

    private void item(OrgContext ctx, Offboarding o, String title, LocalDate due, int order) {
        OffboardingItem i = new OffboardingItem();
        i.setId(Ids.next());
        i.setOrgId(ctx.orgId());
        i.setOffboardingId(o.getId());
        i.setTitle(title);
        i.setDueDate(due);
        i.setSortOrder((short) order);
        i.setCreatedAt(clock.instant());
        items.save(i);
    }

    private OffboardingDetail detail(OrgContext ctx, Offboarding o) {
        List<OffboardingItem> is = items.findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(o.getId());
        Map<UUID, PersonRef> people = profiles.persons(Stream.concat(Stream.of(o.getUserId(), o.getRequestedBy(), o.getStartedBy(),
                o.getCompletedBy()), is.stream().map(OffboardingItem::getDoneBy)).filter(Objects::nonNull).toList());
        boolean manage = HrAccess.managesPerson(ctx, o.getUserId()) && !o.getUserId().equals(ctx.userId());
        Handover handover = null;
        List<com.businessplatform.hr.payload.HrPayloads.Balance> leave = List.of();
        if (o.open()) {
            List<UUID> reports = profileRows.findByOrgId(ctx.orgId()).stream()
                    .filter(p -> o.getUserId().equals(p.getManagerId()) && active(ctx, p.getUserId())).map(EmployeeProfile::getUserId).toList();
            Map<UUID, PersonRef> reportRefs = profiles.persons(reports);
            handover = new Handover(reports.stream().map(reportRefs::get).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(PersonRef::name, String.CASE_INSENSITIVE_ORDER)).toList(),
                    approvals.waitingOn(ctx.orgId(), o.getUserId()), structure.unitsHeadedBy(ctx.orgId(), o.getUserId()));
            if (active(ctx, o.getUserId())) {
                leave = balances.balances(ctx, o.getUserId(), o.getLastDay().getYear(), false);
            }
        }
        return new OffboardingDetail(summary(o, people.get(o.getUserId()), is), o.getReason(), o.getRehire(),
                is.stream().map(i -> new ItemView(i.getId(), i.getTitle(), i.getDueDate(), i.getDoneAt() != null, i.getDoneAt(),
                        i.getDoneBy() == null ? null : people.get(i.getDoneBy()))).toList(),
                handover, leave, ref(people, o.getRequestedBy()), ref(people, o.getStartedBy()), ref(people, o.getCompletedBy()),
                o.getCompletedAt(), manage && o.open(), o.open() && canCancel(ctx, o));
    }

    private OffboardingSummary summary(Offboarding o, PersonRef person) {
        return summary(o, person, items.findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(o.getId()));
    }

    private static OffboardingSummary summary(Offboarding o, PersonRef person, List<OffboardingItem> is) {
        return new OffboardingSummary(o.getId(), person, o.getKind(), o.getStatus(), o.getNoticeOn(), o.getLastDay(),
                (int) is.stream().filter(i -> i.getDoneAt() != null).count(), is.size());
    }

    private static PersonRef ref(Map<UUID, PersonRef> people, UUID id) {
        return id == null ? null : people.get(id);
    }

    private boolean canCancel(OrgContext ctx, Offboarding o) {
        boolean hrm = HrAccess.managesPerson(ctx, o.getUserId()) && !o.getUserId().equals(ctx.userId());
        return hrm || (o.getUserId().equals(ctx.userId()) && Offboarding.REQUESTED.equals(o.getStatus()));
    }

    /** HR for this person, never for oneself. */
    private static void requireFor(OrgContext ctx, UUID userId) {
        if (userId.equals(ctx.userId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Someone else in HR handles your own departure");
        }
        if (!HrAccess.managesPerson(ctx, userId)) {
            throw BusinessException.forbidden();
        }
    }

    private void noneOpen(OrgContext ctx, UUID userId) {
        if (offboardings.findByOrgIdAndUserIdOrderByCreatedAtDesc(ctx.orgId(), userId).stream().anyMatch(Offboarding::open)) {
            throw new BusinessException(ErrorCode.CONFLICT, "A departure is already under way for this person", List.of());
        }
    }

    private Offboarding find(OrgContext ctx, UUID id) {
        return offboardings.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Offboarding open(OrgContext ctx, UUID id) {
        Offboarding o = find(ctx, id);
        if (!o.open()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This departure is closed", List.of());
        }
        return o;
    }

    private boolean active(OrgContext ctx, UUID userId) {
        return memberships.existsByOrgIdAndUserIdAndStatus(ctx.orgId(), userId, Membership.ACTIVE);
    }

    private List<UUID> admins(OrgContext ctx) {
        return memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole().isAdmin()).map(Membership::getUserId).toList();
    }

    private String name(UUID userId) {
        PersonRef r = profiles.persons(List.of(userId)).get(userId);
        return r == null ? "" : r.name();
    }

    private static String link(OrgContext ctx, Offboarding o) {
        return "/" + ctx.slug() + "/hr/offboarding/" + o.getId();
    }

    private void audit(OrgContext ctx, Offboarding o, String verb, Map<String, Object> data) {
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, o.getId(), ctx.userId(), verb, data);
    }
}
