package com.businessplatform.hr.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.OvertimeRequest;
import com.businessplatform.hr.payload.AttendancePayloads.OvertimeInput;
import com.businessplatform.hr.payload.AttendancePayloads.OvertimeView;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.payload.WorkflowPayloads.ApprovalDetail;
import com.businessplatform.workflow.payload.WorkflowPayloads.StepInput;
import com.businessplatform.workflow.payload.WorkflowPayloads.SubmitRequest;
import com.businessplatform.workflow.service.ApprovalClosed;
import com.businessplatform.workflow.service.ApprovalService;
import com.businessplatform.workflow.service.WorkflowService;

import lombok.RequiredArgsConstructor;

/**
 * Overtime: extra minutes worked on a past day (up to 60 days back), asked for afterwards and sent through the
 * approval engine on the organisation's HR workflow (by default the person's manager, like leave). Approved
 * overtime shows on the timesheet. One open or approved request per person and day.
 */
@Service
@RequiredArgsConstructor
public class OvertimeService {

    /** How overtime requests appear in the approval engine and the activity log. */
    public static final String RECORD = "overtime";
    static final int MAX_DAYS_BACK = 60;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final List<String> OPEN = List.of(OvertimeRequest.PENDING, OvertimeRequest.APPROVED);

    private final HrAccess hr;
    private final ProfileService profiles;
    private final OvertimeRequestRepository requests;
    private final WorkflowService workflows;
    private final ApprovalService approvals;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional
    public OvertimeView create(String slug, OvertimeInput in) {
        OrgContext ctx = hr.require(slug);
        profiles.profile(ctx, ctx.userId());
        LocalDate today = LeaveService.today(clock);
        if (in.workDate().isAfter(today)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "workDate", "Ask for overtime after you have worked it");
        }
        if (in.workDate().isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "workDate", "At most 60 days back");
        }
        if (requests.existsByOrgIdAndUserIdAndWorkDateAndStatusIn(ctx.orgId(), ctx.userId(), in.workDate(), OPEN)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "workDate", "You already asked for overtime on this day");
        }
        OvertimeRequest draft = new OvertimeRequest();
        draft.setId(Ids.next());
        draft.setOrgId(ctx.orgId());
        draft.setUserId(ctx.userId());
        draft.setWorkDate(in.workDate());
        draft.setMinutes((short) in.minutes());
        draft.setReason(in.reason().strip());
        draft.setStatus(OvertimeRequest.PENDING);
        draft.setCreatedAt(clock.instant());
        draft.setUpdatedAt(draft.getCreatedAt());
        OvertimeRequest o = requests.saveAndFlush(draft);
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, o.getId(), ctx.userId(), "overtime.requested",
                Map.of("date", o.getWorkDate().toString(), "minutes", (int) o.getMinutes()));
        UUID workflow = workflows.ensure(ctx, HrAccess.MODULE, LeaveService.WORKFLOW, new StepInput("MANAGER", null, null));
        ApprovalDetail d = approvals.submit(slug, new SubmitRequest(workflow, HrAccess.MODULE, RECORD, o.getId(), o.getReason()));
        o.setApprovalId(d.summary().id());
        return view(ctx, o);
    }

    /** Mine, newest first. */
    @Transactional(readOnly = true)
    public List<OvertimeView> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        return views(ctx, requests.findByOrgIdAndUserIdOrderByWorkDateDesc(ctx.orgId(), ctx.userId()));
    }

    /** HR managers: everyone's, newest first; {@code status} narrows it. */
    @Transactional(readOnly = true)
    public List<OvertimeView> all(String slug, String status) {
        OrgContext ctx = hr.requireManager(slug);
        return views(ctx, status == null || status.isBlank() ? requests.findByOrgIdOrderByWorkDateDesc(ctx.orgId())
                : requests.findByOrgIdAndStatusInOrderByWorkDateDesc(ctx.orgId(), List.of(status)));
    }

    @Transactional
    public OvertimeView withdraw(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        OvertimeRequest o = requests.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!o.getUserId().equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        if (!OvertimeRequest.PENDING.equals(o.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a request that is waiting can be withdrawn", List.of());
        }
        if (o.getApprovalId() != null) {
            approvals.withdraw(slug, o.getApprovalId());
        }
        if (OvertimeRequest.PENDING.equals(o.getStatus())) {
            settle(o, OvertimeRequest.WITHDRAWN);
        }
        return view(ctx, o);
    }

    /** The approval engine closed an overtime request's approval: settle the request. */
    @EventListener
    @Transactional
    public void onApprovalClosed(ApprovalClosed e) {
        if (!HrAccess.MODULE.equals(e.module()) || !RECORD.equals(e.recordType())) {
            return;
        }
        requests.findByIdAndOrgId(e.recordId(), e.ctx().orgId())
                .filter(o -> OvertimeRequest.PENDING.equals(o.getStatus()) && e.requestId().equals(o.getApprovalId()))
                .ifPresent(o -> settle(o, switch (e.status()) {
                    case "APPROVED" -> OvertimeRequest.APPROVED;
                    case "REJECTED" -> OvertimeRequest.REJECTED;
                    default -> OvertimeRequest.WITHDRAWN;
                }));
    }

    /** "Overtime · Dara Sok · 3 Nov 2026 (2 h 30 min)", the approval's title. */
    static String title(OvertimeRequest o, AppUser who) {
        int h = o.getMinutes() / 60;
        int m = o.getMinutes() % 60;
        String length = h == 0 ? m + " min" : m == 0 ? h + " h" : h + " h " + m + " min";
        return "Overtime · " + who.getName() + " · " + DAY.format(o.getWorkDate()) + " (" + length + ")";
    }

    static String link(OrgContext ctx) {
        return "/" + ctx.slug() + "/hr/attendance";
    }

    private void settle(OvertimeRequest o, String status) {
        o.setStatus(status);
        o.setDecidedAt(clock.instant());
        o.setUpdatedAt(clock.instant());
    }

    private OvertimeView view(OrgContext ctx, OvertimeRequest o) {
        return views(ctx, List.of(o)).get(0);
    }

    private List<OvertimeView> views(OrgContext ctx, Collection<OvertimeRequest> found) {
        Map<UUID, PersonRef> people = profiles.persons(found.stream().map(OvertimeRequest::getUserId).toList());
        Map<UUID, String> approvalStatus = approvals.statuses(ctx.orgId(),
                found.stream().map(OvertimeRequest::getApprovalId).filter(Objects::nonNull).toList());
        Map<UUID, UUID> managers = new HashMap<>();
        List<OvertimeView> out = new ArrayList<>();
        for (OvertimeRequest o : found) {
            boolean mine = o.getUserId().equals(ctx.userId());
            boolean manager = ctx.userId().equals(managers.computeIfAbsent(o.getUserId(),
                    u -> profiles.managerOf(ctx.orgId(), u).orElse(null)));
            out.add(new OvertimeView(o.getId(), people.get(o.getUserId()), o.getWorkDate(), o.getMinutes(),
                    mine || manager || HrAccess.manages(ctx) ? o.getReason() : null, o.getStatus(), o.getApprovalId(),
                    o.getApprovalId() == null ? null : approvalStatus.get(o.getApprovalId()), o.getCreatedAt(),
                    mine && OvertimeRequest.PENDING.equals(o.getStatus())));
        }
        return out;
    }
}
