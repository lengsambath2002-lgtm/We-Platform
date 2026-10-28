package com.businessplatform.workflow.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.domain.ApprovalAction;
import com.businessplatform.workflow.domain.ApprovalRequest;
import com.businessplatform.workflow.domain.ApprovalStep;
import com.businessplatform.workflow.domain.WorkflowDefinition;
import com.businessplatform.workflow.domain.WorkflowStepDef;
import com.businessplatform.workflow.payload.WorkflowPayloads.*;
import com.businessplatform.workflow.repository.WorkflowRepositories;

import lombok.RequiredArgsConstructor;

/**
 * The approval engine for any module's records.
 *
 * <pre>
 * submit ─► PENDING ─approve (last step)─► APPROVED
 *              │  └─approve─► next step PENDING
 *              ├─reject───────────────────► REJECTED
 *              ├─request changes─► CHANGES_REQUESTED ─resubmit─► PENDING (same step)
 *              └─withdraw (requester, while open)──► WITHDRAWN
 * </pre>
 *
 * Before a request is shown or decided, the owning module's {@link RecordResolver} confirms, on the
 * server, that the person may see the record. Nobody approves their own request. One open request per
 * record. Every step writes the request's history, the record's activity and notifications.
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final OrgAccess access;
    private final ModuleService modules;
    private final List<RecordResolver> resolvers;
    private final WorkflowRepositories.Definitions definitions;
    private final WorkflowRepositories.StepDefs stepDefs;
    private final WorkflowRepositories.Requests requests;
    private final WorkflowRepositories.Steps steps;
    private final WorkflowRepositories.Actions actions;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Submit ───────────────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalDetail submit(String slug, SubmitRequest in) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), in.moduleKey());
        RecordResolver resolver = resolver(in.moduleKey(), in.recordType());
        RecordResolver.RecordView record = resolver.view(ctx, in.recordType(), in.recordId())
                .orElseThrow(BusinessException::notFound);
        if (!record.canRequestApproval()) {
            throw BusinessException.forbidden();
        }
        WorkflowDefinition w = definitions.findByIdAndOrgId(in.workflowId(), ctx.orgId())
                .filter(WorkflowDefinition::isActive).filter(d -> d.getModuleKey().equals(in.moduleKey()))
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "workflowId", "Choose an active workflow"));
        boolean open = requests.findByOrgIdAndModuleKeyAndRecordTypeAndRecordIdOrderByCreatedAtDesc(ctx.orgId(),
                in.moduleKey(), in.recordType(), in.recordId()).stream().anyMatch(ApprovalRequest::open);
        if (open) {
            throw BusinessException.field(ErrorCode.CONFLICT, "recordId", "This already has an open approval request");
        }
        ApprovalRequest r = new ApprovalRequest();
        r.setId(Ids.next());
        r.setOrgId(ctx.orgId());
        r.setWorkflowId(w.getId());
        r.setModuleKey(in.moduleKey());
        r.setRecordType(in.recordType());
        r.setRecordId(in.recordId());
        r.setTitle(record.title().length() > 200 ? record.title().substring(0, 200) : record.title());
        r.setSummary(in.summary() == null || in.summary().isBlank() ? null : in.summary().strip());
        r.setRequesterId(ctx.userId());
        r.setStatus(ApprovalRequest.PENDING);
        r.setCurrentStep((short) 1);
        requests.saveAndFlush(r);
        List<WorkflowStepDef> defs = stepDefs.findByWorkflowIdOrderByStepOrder(w.getId());
        for (WorkflowStepDef d : defs) {
            ApprovalStep s = new ApprovalStep();
            s.setId(Ids.next());
            s.setRequestId(r.getId());
            s.setStepOrder(d.getStepOrder());
            s.setStatus(d.getStepOrder() == 1 ? "PENDING" : "WAITING");
            switch (d.getApproverType()) {
                case "USER" -> s.setApproverId(d.getApproverUser());
                case "ORG_ROLE" -> s.setApproverRole(d.getApproverRole());
                case "PROJECT_LEAD" -> s.setApproverId(resolver.leadOf(ctx, in.recordType(), in.recordId())
                        .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "workflowId",
                                "This record has no lead to approve it")));
                default -> throw new IllegalStateException(d.getApproverType());
            }
            if (ctx.userId().equals(s.getApproverId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "workflowId",
                        "You can't approve your own request — choose another workflow");
            }
            steps.save(s);
        }
        log(r, ctx.userId(), "SUBMIT", (short) 1, r.getSummary());
        recordActivity(ctx, r, "approval.requested", Map.of("workflow", w.getName()));
        notifyApprovers(ctx, r, "approval.requested", Text.of("Approval requested: {title}", Map.of("title", r.getTitle())), record.link());
        return detail(ctx, r);
    }

    // ── Read ─────────────────────────────────────────────────────────────────────────

    /** {@code box}: inbox (waiting on me), sent (mine), all (Owners / Admins). Only records I may see. */
    @Transactional(readOnly = true)
    public List<ApprovalSummary> list(String slug, String box) {
        OrgContext ctx = access.require(slug);
        List<ApprovalRequest> found = switch (box == null ? "inbox" : box) {
            case "sent" -> requests.findByOrgIdAndRequesterIdOrderByUpdatedAtDesc(ctx.orgId(), ctx.userId());
            case "all" -> {
                if (!ctx.isAdmin()) {
                    throw BusinessException.forbidden();
                }
                yield requests.findTop200ByOrgIdOrderByUpdatedAtDesc(ctx.orgId());
            }
            case "inbox" -> requests.inbox(ctx.orgId(), ctx.userId(), roleKeys(ctx)).stream()
                    .filter(r -> !r.getRequesterId().equals(ctx.userId())).toList();
            default -> throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "box", "inbox, sent or all");
        };
        List<ApprovalSummary> out = new ArrayList<>();
        Map<UUID, PersonView> ps = persons(found.stream().map(ApprovalRequest::getRequesterId).toList());
        for (ApprovalRequest r : found) {
            visible(ctx, r).ifPresent(view -> out.add(summary(r, view, ps)));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ApprovalDetail get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        ApprovalRequest r = requests.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        return detail(ctx, r);
    }

    /** Requests about one record (its history panel), only if the caller may see the record. */
    @Transactional(readOnly = true)
    public List<ApprovalSummary> forRecord(String slug, String module, String recordType, UUID recordId) {
        OrgContext ctx = access.require(slug);
        RecordResolver.RecordView view = resolver(module, recordType).view(ctx, recordType, recordId)
                .orElseThrow(BusinessException::notFound);
        List<ApprovalRequest> found = requests.findByOrgIdAndModuleKeyAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
                ctx.orgId(), module, recordType, recordId);
        Map<UUID, PersonView> ps = persons(found.stream().map(ApprovalRequest::getRequesterId).toList());
        return found.stream().map(r -> summary(r, view, ps)).toList();
    }

    // ── Decide ───────────────────────────────────────────────────────────────────────

    @Transactional
    public ApprovalDetail approve(String slug, UUID id, DecisionRequest in) {
        return decide(slug, id, "APPROVE", in == null ? null : in.comment());
    }

    @Transactional
    public ApprovalDetail reject(String slug, UUID id, DecisionRequest in) {
        return decide(slug, id, "REJECT", requireComment(in));
    }

    @Transactional
    public ApprovalDetail requestChanges(String slug, UUID id, DecisionRequest in) {
        return decide(slug, id, "REQUEST_CHANGES", requireComment(in));
    }

    private ApprovalDetail decide(String slug, UUID id, String action, String comment) {
        OrgContext ctx = access.require(slug);
        ApprovalRequest r = requests.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        RecordResolver.RecordView view = visible(ctx, r).orElseThrow(BusinessException::notFound);
        if (!ApprovalRequest.PENDING.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This request is not waiting for a decision", List.of());
        }
        ApprovalStep step = current(r);
        if (!canDecide(ctx, r, step)) {
            throw BusinessException.forbidden();
        }
        step.setDecidedBy(ctx.userId());
        step.setDecidedAt(clock.instant());
        String verb;
        switch (action) {
            case "APPROVE" -> {
                step.setStatus("APPROVED");
                Optional<ApprovalStep> next = steps.findByRequestIdOrderByStepOrder(r.getId()).stream()
                        .filter(s -> s.getStepOrder() == step.getStepOrder() + 1).findFirst();
                if (next.isPresent()) {
                    next.get().setStatus("PENDING");
                    r.setCurrentStep(next.get().getStepOrder());
                    verb = "approval.step.approved";
                } else {
                    r.setStatus(ApprovalRequest.APPROVED);
                    r.setDecidedAt(clock.instant());
                    verb = "approval.approved";
                }
            }
            case "REJECT" -> {
                step.setStatus("REJECTED");
                r.setStatus(ApprovalRequest.REJECTED);
                r.setDecidedAt(clock.instant());
                verb = "approval.rejected";
            }
            case "REQUEST_CHANGES" -> {
                step.setStatus("RETURNED");
                r.setStatus(ApprovalRequest.CHANGES_REQUESTED);
                verb = "approval.changes_requested";
            }
            default -> throw new IllegalStateException(action);
        }
        log(r, ctx.userId(), action, step.getStepOrder(), comment);
        recordActivity(ctx, r, verb, comment == null ? Map.of("step", step.getStepOrder())
                : Map.of("step", step.getStepOrder(), "comment", comment));
        Map<String, Object> about = Map.of("title", r.getTitle(), "step", step.getStepOrder());
        Text outcome = switch (verb) {
            case "approval.approved" -> Text.of("Approved: {title}", about);
            case "approval.rejected" -> Text.of("Rejected: {title}", about);
            case "approval.changes_requested" -> Text.of("Changes requested: {title}", about);
            default -> Text.of("Step {step} approved: {title}", about);
        };
        notifications.send(ctx.orgId(), List.of(r.getRequesterId()), ctx.userId(), verb, outcome, Text.raw(comment),
                view.link());
        if ("approval.step.approved".equals(verb)) {
            notifyApprovers(ctx, r, "approval.requested", Text.of("Approval requested: {title}", Map.of("title", r.getTitle())), view.link());
        }
        return detail(ctx, r);
    }

    @Transactional
    public ApprovalDetail resubmit(String slug, UUID id, DecisionRequest in) {
        OrgContext ctx = access.require(slug);
        ApprovalRequest r = requests.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        RecordResolver.RecordView view = visible(ctx, r).orElseThrow(BusinessException::notFound);
        if (!r.getRequesterId().equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        if (!ApprovalRequest.CHANGES_REQUESTED.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a request with changes requested can be resubmitted", List.of());
        }
        ApprovalStep step = current(r);
        step.setStatus("PENDING");
        step.setDecidedBy(null);
        step.setDecidedAt(null);
        r.setStatus(ApprovalRequest.PENDING);
        String comment = in == null || in.comment() == null || in.comment().isBlank() ? null : in.comment().strip();
        log(r, ctx.userId(), "RESUBMIT", step.getStepOrder(), comment);
        recordActivity(ctx, r, "approval.resubmitted", comment == null ? Map.of() : Map.of("comment", comment));
        notifyApprovers(ctx, r, "approval.resubmitted",
                Text.of("Resubmitted for approval: {title}", Map.of("title", r.getTitle())), view.link());
        return detail(ctx, r);
    }

    @Transactional
    public ApprovalDetail withdraw(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        ApprovalRequest r = requests.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        visible(ctx, r).orElseThrow(BusinessException::notFound);
        if (!r.getRequesterId().equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        if (!r.open()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This request is already closed", List.of());
        }
        r.setStatus(ApprovalRequest.WITHDRAWN);
        r.setDecidedAt(clock.instant());
        log(r, ctx.userId(), "WITHDRAW", r.getCurrentStep(), null);
        recordActivity(ctx, r, "approval.withdrawn", Map.of());
        return detail(ctx, r);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private ApprovalDetail detail(OrgContext ctx, ApprovalRequest r) {
        RecordResolver.RecordView view = visible(ctx, r).orElseThrow(BusinessException::notFound);
        boolean involved = r.getRequesterId().equals(ctx.userId()) || ctx.isAdmin()
                || steps.findByRequestIdOrderByStepOrder(r.getId()).stream().anyMatch(s -> canAct(ctx, s));
        if (!involved) {
            throw BusinessException.notFound();
        }
        List<ApprovalStep> ss = steps.findByRequestIdOrderByStepOrder(r.getId());
        List<ApprovalAction> history = actions.findByRequestIdOrderByCreatedAt(r.getId());
        Map<UUID, PersonView> ps = persons(Stream.of(
                ss.stream().flatMap(s -> Stream.of(s.getApproverId(), s.getDecidedBy())),
                history.stream().map(ApprovalAction::getActorId), Stream.of(r.getRequesterId()))
                .flatMap(Function.identity()).filter(Objects::nonNull).collect(Collectors.toSet()));
        WorkflowDefinition w = definitions.findById(r.getWorkflowId()).orElseThrow();
        boolean pending = ApprovalRequest.PENDING.equals(r.getStatus());
        boolean mine = r.getRequesterId().equals(ctx.userId());
        return new ApprovalDetail(summary(r, view, ps), r.getSummary(), w.getName(),
                ss.stream().map(s -> new StepState(s.getStepOrder(), s.getApproverId() == null ? null : ps.get(s.getApproverId()),
                        s.getApproverRole(), s.getStatus(), s.getDecidedBy() == null ? null : ps.get(s.getDecidedBy()),
                        s.getDecidedAt())).toList(),
                history.stream().map(a -> new ActionView(ps.get(a.getActorId()), a.getAction(),
                        a.getStepOrder() == null ? null : (int) a.getStepOrder(), a.getComment(), a.getCreatedAt())).toList(),
                pending && canDecide(ctx, r, current(r)),
                mine && ApprovalRequest.CHANGES_REQUESTED.equals(r.getStatus()), mine && r.open());
    }

    private Optional<RecordResolver.RecordView> visible(OrgContext ctx, ApprovalRequest r) {
        return resolver(r.getModuleKey(), r.getRecordType()).view(ctx, r.getRecordType(), r.getRecordId());
    }

    private ApprovalStep current(ApprovalRequest r) {
        return steps.findByRequestIdOrderByStepOrder(r.getId()).stream()
                .filter(s -> s.getStepOrder() == r.getCurrentStep()).findFirst().orElseThrow();
    }

    private boolean canDecide(OrgContext ctx, ApprovalRequest r, ApprovalStep step) {
        return "PENDING".equals(step.getStatus()) && !r.getRequesterId().equals(ctx.userId()) && canAct(ctx, step);
    }

    private static boolean canAct(OrgContext ctx, ApprovalStep step) {
        if (step.getApproverId() != null) {
            return step.getApproverId().equals(ctx.userId());
        }
        return roleKeys(ctx).contains(step.getApproverRole());
    }

    /** ADMIN steps accept Owners too (an Owner can do whatever an Admin can). */
    private static List<String> roleKeys(OrgContext ctx) {
        return switch (ctx.role()) {
            case OWNER -> List.of("OWNER", "ADMIN");
            case ADMIN -> List.of("ADMIN");
            default -> List.of("__none__");
        };
    }

    private void notifyApprovers(OrgContext ctx, ApprovalRequest r, String type, Text title, String link) {
        ApprovalStep step = current(r);
        Collection<UUID> to;
        if (step.getApproverId() != null) {
            to = List.of(step.getApproverId());
        } else {
            Set<OrgRole> roles = "OWNER".equals(step.getApproverRole()) ? Set.of(OrgRole.OWNER)
                    : Set.of(OrgRole.OWNER, OrgRole.ADMIN);
            to = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                    .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && roles.contains(m.getRole()))
                    .map(Membership::getUserId).filter(u -> !u.equals(r.getRequesterId())).collect(Collectors.toCollection(HashSet::new));
        }
        notifications.send(ctx.orgId(), to, ctx.userId(), type, title, Text.raw(r.getSummary()), link);
    }

    private void recordActivity(OrgContext ctx, ApprovalRequest r, String verb, Map<String, Object> data) {
        Map<String, Object> d = new java.util.HashMap<>(data);
        d.put("requestId", r.getId().toString());
        activity.record(ctx.orgId(), r.getModuleKey(), r.getRecordType(), r.getRecordId(), ctx.userId(), verb, d);
    }

    private void log(ApprovalRequest r, UUID actor, String action, Short step, String comment) {
        ApprovalAction a = new ApprovalAction();
        a.setId(Ids.next());
        a.setRequestId(r.getId());
        a.setActorId(actor);
        a.setAction(action);
        a.setStepOrder(step);
        a.setComment(comment);
        a.setCreatedAt(clock.instant());
        actions.save(a);
    }

    private RecordResolver resolver(String module, String recordType) {
        return resolvers.stream().filter(x -> x.module().equals(module) && x.handles(recordType)).findFirst()
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "recordType",
                        "This kind of record can't be approved"));
    }

    private static String requireComment(DecisionRequest in) {
        if (in == null || in.comment() == null || in.comment().isBlank()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "comment", "Say why");
        }
        return in.comment().strip();
    }

    private Map<UUID, PersonView> persons(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(wanted).stream()
                .collect(Collectors.toMap(AppUser::getId, u -> new PersonView(u.getId(), u.getName())));
    }

    private ApprovalSummary summary(ApprovalRequest r, RecordResolver.RecordView view, Map<UUID, PersonView> ps) {
        return new ApprovalSummary(r.getId(), r.getTitle(), r.getModuleKey(), r.getRecordType(), r.getRecordId(),
                view.link(), r.getStatus(), r.getCurrentStep() == null ? 0 : r.getCurrentStep(),
                (int) steps.findByRequestIdOrderByStepOrder(r.getId()).size(), ps.get(r.getRequesterId()),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
