package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.payload.HrPayloads.*;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.payload.WorkflowPayloads.ApprovalDetail;
import com.businessplatform.workflow.payload.WorkflowPayloads.StepInput;
import com.businessplatform.workflow.payload.WorkflowPayloads.SubmitRequest;
import com.businessplatform.workflow.service.ApprovalClosed;
import com.businessplatform.workflow.service.ApprovalService;
import com.businessplatform.workflow.service.WorkflowService;

import lombok.RequiredArgsConstructor;

/**
 * Leave requests. Asking for leave checks the dates (working days, half days, no overlap, enough days
 * left) and sends the request through the approval engine on the organisation's HR workflow — by default
 * "Manager sign-off": the requester's manager, or the Admins when nobody is set. The engine's outcome
 * ({@link ApprovalClosed}) settles the request: approved days are used, rejected or withdrawn ones are
 * given back. Approved leave that hasn't started can be cancelled, which gives the days back too.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    /** How leave requests appear in the approval engine and the activity log. */
    public static final String RECORD = "leave";
    public static final String WORKFLOW = "Manager sign-off";
    static final int MAX_RANGE_DAYS = 62;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final HrAccess hr;
    private final ProfileService profiles;
    private final LeaveTypeService types;
    private final BalanceService balances;
    private final LeaveRequestRepository leaves;
    private final EmployeeProfileRepository profileRows;
    private final UserRepository users;
    private final WorkflowService workflows;
    private final ApprovalService approvals;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Ask ──────────────────────────────────────────────────────────────────────────

    @Transactional
    public LeaveView create(String slug, LeaveInput in) {
        OrgContext ctx = hr.require(slug);
        LeaveType type = types.find(ctx, in.leaveTypeId());
        if (!type.isActive()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "leaveTypeId", "This leave type is switched off");
        }
        LocalDate start = in.startDate();
        LocalDate end = in.endDate();
        if (end.isBefore(start)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endDate", "The last day is before the first");
        }
        if (start.getYear() != end.getYear()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endDate", "Split leave that crosses into a new year into two requests");
        }
        if ((in.halfStart() || in.halfEnd()) && !type.isHalfDays()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "halfStart", "This leave type is taken in whole days");
        }
        if (start.equals(end) && in.halfStart() && in.halfEnd()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "halfEnd", "For half a day, choose the morning or the afternoon");
        }
        BigDecimal days = LeaveDays.count(start, end, in.halfStart(), in.halfEnd());
        if (days.signum() == 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "startDate", "These days are all on a weekend");
        }
        // One request at a time per person, so two quick requests can't both spend the last days.
        profiles.profile(ctx, ctx.userId());
        profileRows.lock(ctx.orgId(), ctx.userId());
        if (leaves.overlaps(ctx.orgId(), ctx.userId(), start, end)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "startDate", "You already have leave on some of these days");
        }
        BigDecimal left = balances.remaining(ctx, ctx.userId(), type, start.getYear());
        if (left != null && days.compareTo(left) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endDate", "Not enough days left of this leave type");
        }
        LeaveRequest draft = new LeaveRequest();
        draft.setId(Ids.next());
        draft.setOrgId(ctx.orgId());
        draft.setUserId(ctx.userId());
        draft.setLeaveTypeId(type.getId());
        draft.setStartDate(start);
        draft.setEndDate(end);
        draft.setHalfStart(in.halfStart() && type.isHalfDays());
        draft.setHalfEnd(in.halfEnd() && type.isHalfDays());
        draft.setDays(days);
        draft.setReason(ProfileService.clean(in.reason()));
        draft.setStatus(LeaveRequest.PENDING);
        draft.setCreatedAt(clock.instant());
        draft.setUpdatedAt(draft.getCreatedAt());
        // Ids are assigned here, so save() merges: keep working on the managed copy it returns.
        LeaveRequest l = leaves.saveAndFlush(draft);
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, l.getId(), ctx.userId(), "leave.requested",
                Map.of("type", type.getNameEn(), "start", start.toString(), "end", end.toString(), "days", days.toPlainString()));
        UUID workflow = workflows.ensure(ctx, HrAccess.MODULE, WORKFLOW, new StepInput("MANAGER", null, null));
        ApprovalDetail d = approvals.submit(slug, new SubmitRequest(workflow, HrAccess.MODULE, RECORD, l.getId(), l.getReason()));
        l.setApprovalId(d.summary().id());
        return view(ctx, l);
    }

    // ── Read ─────────────────────────────────────────────────────────────────────────
    // Not read-only: the first use of HR may seed the default leave types.

    /** The caller's requests, newest first. */
    @Transactional
    public List<LeaveView> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        return views(ctx, leaves.findByOrgIdAndUserIdOrderByStartDateDesc(ctx.orgId(), ctx.userId()));
    }

    /** Everyone's requests for HR managers (at most 500), filtered; {@code year} by the first day. */
    @Transactional
    public List<LeaveView> all(String slug, String status, UUID userId, UUID typeId, Integer year) {
        OrgContext ctx = hr.requireManager(slug);
        Specification<LeaveRequest> spec = (l, q, cb) -> cb.equal(l.get("orgId"), ctx.orgId());
        if (status != null && !status.isBlank()) {
            spec = spec.and((l, q, cb) -> cb.equal(l.get("status"), status));
        }
        if (userId != null) {
            spec = spec.and((l, q, cb) -> cb.equal(l.get("userId"), userId));
        }
        if (typeId != null) {
            spec = spec.and((l, q, cb) -> cb.equal(l.get("leaveTypeId"), typeId));
        }
        if (year != null) {
            LocalDate from = LocalDate.of(year, 1, 1);
            LocalDate to = LocalDate.of(year, 12, 31);
            spec = spec.and((l, q, cb) -> cb.between(l.get("startDate"), from, to));
        }
        return views(ctx, leaves.findAll(spec, PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "startDate", "createdAt")))
                .getContent());
    }

    @Transactional
    public LeaveView get(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        return view(ctx, find(ctx, id));
    }

    /** Who is away on some day of {@code from}–{@code to} (approved leave only). */
    @Transactional
    public List<Absence> absences(String slug, LocalDate from, LocalDate to) {
        OrgContext ctx = hr.require(slug);
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "At most 62 days");
        }
        List<LeaveRequest> found = leaves.approvedBetween(ctx.orgId(), from, to);
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(LeaveRequest::getUserId).toList());
        Map<UUID, LeaveType> byId = typesById(ctx);
        return found.stream().filter(l -> people.containsKey(l.getUserId()))
                .map(l -> new Absence(l.getId(), people.get(l.getUserId()), l.getStartDate(), l.getEndDate(),
                        l.isHalfStart(), l.isHalfEnd(), HrAccess.manages(ctx) || l.getUserId().equals(ctx.userId())
                                ? LeaveTypeService.view(byId.get(l.getLeaveTypeId())) : null))
                .toList();
    }

    // ── Change ───────────────────────────────────────────────────────────────────────

    /** The requester takes back a request that is still waiting (through the engine, which settles it). */
    @Transactional
    public LeaveView withdraw(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        LeaveRequest l = find(ctx, id);
        if (!l.getUserId().equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        if (!LeaveRequest.PENDING.equals(l.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a request that is waiting can be withdrawn", List.of());
        }
        if (l.getApprovalId() != null) {
            approvals.withdraw(slug, l.getApprovalId());
        }
        if (LeaveRequest.PENDING.equals(l.getStatus())) {
            settle(l, LeaveRequest.WITHDRAWN);
        }
        return view(ctx, l);
    }

    /**
     * Calls off approved leave and gives its days back: the person, for leave that hasn't started; HR
     * managers, for anyone's approved leave (to correct the record).
     */
    @Transactional
    public LeaveView cancel(String slug, UUID id, CancelRequest in) {
        OrgContext ctx = hr.require(slug);
        LeaveRequest l = find(ctx, id);
        boolean mine = l.getUserId().equals(ctx.userId());
        if (!mine && !HrAccess.manages(ctx)) {
            throw BusinessException.forbidden();
        }
        if (!LeaveRequest.APPROVED.equals(l.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only approved leave can be cancelled", List.of());
        }
        if (!HrAccess.manages(ctx) && !l.getStartDate().isAfter(today(clock))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Leave that has started can only be cancelled by HR", List.of());
        }
        l.setStatus(LeaveRequest.CANCELLED);
        l.setCancelledBy(ctx.userId());
        l.setCancelReason(in == null ? null : ProfileService.clean(in.reason()));
        l.setUpdatedAt(clock.instant());
        String title = title(l, types.find(ctx, l.getLeaveTypeId()), users.findById(l.getUserId()).orElseThrow());
        Map<String, Object> data = new HashMap<>(Map.of("days", l.getDays().toPlainString()));
        if (l.getCancelReason() != null) {
            data.put("reason", l.getCancelReason());
        }
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, l.getId(), ctx.userId(), "leave.cancelled", data);
        List<UUID> to = Stream.of(l.getUserId(), profiles.managerOf(ctx.orgId(), l.getUserId()).orElse(null))
                .filter(Objects::nonNull).toList();
        notifications.send(ctx.orgId(), to, ctx.userId(), "leave.cancelled",
                Text.of("Leave cancelled: {title}", Map.of("title", title)), Text.raw(l.getCancelReason()), link(ctx, l));
        return view(ctx, l);
    }

    /** The approval engine closed a leave request's approval: settle the request. */
    @EventListener
    @Transactional
    public void onApprovalClosed(ApprovalClosed e) {
        if (!HrAccess.MODULE.equals(e.module()) || !RECORD.equals(e.recordType())) {
            return;
        }
        leaves.findByIdAndOrgId(e.recordId(), e.ctx().orgId())
                .filter(l -> LeaveRequest.PENDING.equals(l.getStatus()) && e.requestId().equals(l.getApprovalId()))
                .ifPresent(l -> settle(l, switch (e.status()) {
                    case "APPROVED" -> LeaveRequest.APPROVED;
                    case "REJECTED" -> LeaveRequest.REJECTED;
                    default -> LeaveRequest.WITHDRAWN;
                }));
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    LeaveRequest find(OrgContext ctx, UUID id) {
        return leaves.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** "Annual leave · Dara Sok · 3 Nov 2026 – 5 Nov 2026 (3 days)", the approval's title. */
    static String title(LeaveRequest l, LeaveType t, AppUser who) {
        String dates = l.getStartDate().equals(l.getEndDate()) ? DAY.format(l.getStartDate())
                : DAY.format(l.getStartDate()) + " – " + DAY.format(l.getEndDate());
        String days = l.getDays().stripTrailingZeros().toPlainString();
        return t.getNameEn() + " · " + who.getName() + " · " + dates + " (" + days
                + (l.getDays().compareTo(BigDecimal.ONE) == 0 ? " day)" : " days)");
    }

    static String link(OrgContext ctx, LeaveRequest l) {
        return "/" + ctx.slug() + "/hr/leave/" + l.getId();
    }

    /** Today in the organisation's time zone. */
    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }

    private void settle(LeaveRequest l, String status) {
        l.setStatus(status);
        l.setDecidedAt(clock.instant());
        l.setUpdatedAt(clock.instant());
    }

    private Map<UUID, LeaveType> typesById(OrgContext ctx) {
        Map<UUID, LeaveType> byId = new HashMap<>();
        types.all(ctx).forEach(t -> byId.put(t.getId(), t));
        return byId;
    }

    private LeaveView view(OrgContext ctx, LeaveRequest l) {
        return views(ctx, List.of(l)).get(0);
    }

    private List<LeaveView> views(OrgContext ctx, Collection<LeaveRequest> found) {
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(LeaveRequest::getUserId).toList());
        Map<UUID, LeaveType> byId = typesById(ctx);
        Map<UUID, String> approvalStatus = approvals.statuses(ctx.orgId(),
                found.stream().map(LeaveRequest::getApprovalId).filter(Objects::nonNull).toList());
        LocalDate today = today(clock);
        Map<UUID, UUID> managers = new HashMap<>();
        List<LeaveView> out = new ArrayList<>();
        for (LeaveRequest l : found) {
            boolean mine = l.getUserId().equals(ctx.userId());
            boolean manager = ctx.userId().equals(managers.computeIfAbsent(l.getUserId(),
                    u -> profiles.managerOf(ctx.orgId(), u).orElse(null)));
            boolean apt = LeaveRequest.APPROVED.equals(l.getStatus());
            out.add(new LeaveView(l.getId(), people.get(l.getUserId()), LeaveTypeService.view(byId.get(l.getLeaveTypeId())),
                    l.getStartDate(), l.getEndDate(), l.isHalfStart(), l.isHalfEnd(), l.getDays(),
                    mine || manager || HrAccess.manages(ctx) ? l.getReason() : null, l.getStatus(), l.getApprovalId(),
                    l.getApprovalId() == null ? null : approvalStatus.get(l.getApprovalId()),
                    mine || HrAccess.manages(ctx) ? l.getCancelReason() : null, l.getCreatedAt(),
                    mine && LeaveRequest.PENDING.equals(l.getStatus()),
                    apt && (HrAccess.manages(ctx) || (mine && l.getStartDate().isAfter(today)))));
        }
        return out;
    }
}
