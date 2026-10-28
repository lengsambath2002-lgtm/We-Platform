package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.Expense;
import com.businessplatform.business.domain.ExpenseCategory;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.CategoryView;
import com.businessplatform.business.payload.BusinessPayloads.ExpenseRequest;
import com.businessplatform.business.payload.BusinessPayloads.ExpenseView;
import com.businessplatform.business.payload.BusinessPayloads.FileRef;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.business.repository.ExpenseRepository;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.payload.WorkflowPayloads.ApprovalDetail;
import com.businessplatform.workflow.payload.WorkflowPayloads.StepInput;
import com.businessplatform.workflow.payload.WorkflowPayloads.SubmitRequest;
import com.businessplatform.workflow.service.ApprovalCleanup;
import com.businessplatform.workflow.service.ApprovalClosed;
import com.businessplatform.workflow.service.ApprovalService;
import com.businessplatform.workflow.service.WorkflowService;

import lombok.RequiredArgsConstructor;

/**
 * Expenses. Anyone in the module records what they spent (with a receipt) and submits it; it goes through
 * the approval engine on the organisation's business workflow — by default "Expense approval", one step
 * decided by an Admin (created on first use; Admins may change it in Admin → Workflows). The engine's outcome
 * ({@link ApprovalClosed}) settles the expense: APPROVED, REJECTED, or back to DRAFT when withdrawn. Admins
 * then mark approved expenses PAID (reimbursed). In an organisation whose only Owner or Admin is the person
 * submitting, nobody else could decide, so their own expense is approved at once (and the log says so).
 * Members see their own expenses; Admins see everyone's.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    /** How expenses appear in the approval engine and the activity log. */
    public static final String RECORD = "expense";
    public static final String WORKFLOW = "Expense approval";

    private final BusinessAccess access;
    private final ExpenseCategoryService categories;
    private final BizPeople people;
    private final ExpenseTitles titles;
    private final ExpenseRepository expenses;
    private final FileService fileService;
    private final StoredFileRepository files;
    private final MembershipRepository memberships;
    private final WorkflowService workflows;
    private final ApprovalService approvals;
    private final ApprovalCleanup approvalCleanup;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Read ─────────────────────────────────────────────────────────────────────────

    /** Newest first. Members get their own; Admins everyone's, or their own with {@code mine}. */
    @Transactional
    public PageResponse<ExpenseView> list(String slug, String status, boolean mine, int page, int size) {
        OrgContext ctx = access.require(slug);
        Specification<Expense> spec = (e, q, cb) -> cb.equal(e.get("orgId"), ctx.orgId());
        if (mine || !BusinessAccess.manages(ctx)) {
            spec = spec.and((e, q, cb) -> cb.equal(e.get("userId"), ctx.userId()));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((e, q, cb) -> cb.equal(e.get("status"), status));
        }
        Page<Expense> found = expenses.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), CustomerService.MAX_PAGE), Sort.by(Sort.Direction.DESC, "spentOn", "createdAt")));
        return PageResponse.of(new PageImpl<>(views(ctx, found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional
    public ExpenseView get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        return view(ctx, visible(ctx, id));
    }

    /** Waiting for a decision: everyone's for Admins, the caller's own otherwise (at most 10). */
    @Transactional
    public List<ExpenseView> waiting(OrgContext ctx) {
        Specification<Expense> spec = (e, q, cb) -> cb.and(cb.equal(e.get("orgId"), ctx.orgId()),
                cb.equal(e.get("status"), Expense.PENDING));
        if (!BusinessAccess.manages(ctx)) {
            spec = spec.and((e, q, cb) -> cb.equal(e.get("userId"), ctx.userId()));
        }
        return views(ctx, expenses.findAll(spec, PageRequest.of(0, 10, Sort.by("spentOn", "createdAt"))).getContent());
    }

    // ── Change ───────────────────────────────────────────────────────────────────────

    @Transactional
    public ExpenseView create(String slug, ExpenseRequest in) {
        OrgContext ctx = access.require(slug);
        Expense draft = new Expense();
        draft.setId(Ids.next());
        draft.setOrgId(ctx.orgId());
        draft.setUserId(ctx.userId());
        draft.setStatus(Expense.DRAFT);
        draft.setCreatedAt(clock.instant());
        apply(ctx, draft, in, null);
        // Ids are assigned here, so save() merges: keep working on the managed copy it returns.
        Expense e = expenses.saveAndFlush(draft);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.created",
                Map.of("amount", e.getAmount().toPlainString(), "category", categories.find(ctx, e.getCategoryId()).getNameEn()));
        if (in.submit()) {
            submit(ctx, e);
        }
        return view(ctx, e);
    }

    /** A draft, or one the approver sent back for changes, by its person or an Admin. */
    @Transactional
    public ExpenseView update(String slug, UUID id, ExpenseRequest in) {
        OrgContext ctx = access.require(slug);
        Expense e = lock(ctx, id);
        if (!editable(ctx, e, approvalStatus(ctx, e))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft, or an expense sent back for changes, can be edited", List.of());
        }
        apply(ctx, e, in, e.getCategoryId());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.updated",
                Map.of("amount", e.getAmount().toPlainString()));
        if (in.submit() && Expense.DRAFT.equals(e.getStatus()) && e.getUserId().equals(ctx.userId())) {
            submit(ctx, e);
        }
        return view(ctx, e);
    }

    /** Sends a draft for approval (only its person can). */
    @Transactional
    public ExpenseView submit(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Expense e = lock(ctx, id);
        if (!e.getUserId().equals(ctx.userId())) {
            throw BusinessException.forbidden();
        }
        if (!Expense.DRAFT.equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft expense can be submitted", List.of());
        }
        submit(ctx, e);
        return view(ctx, e);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Expense e = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, e.getUserId());
        if (!Expense.DRAFT.equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft expense can be deleted", List.of());
        }
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.deleted",
                Map.of("amount", e.getAmount().toPlainString()));
        UUID receipt = e.getReceiptFileId();
        expenses.delete(e);
        expenses.flush();
        approvalCleanup.recordsDeleted(ctx.orgId(), BusinessAccess.MODULE, RECORD, List.of(e.getId()));
        if (receipt != null) {
            fileService.discard(ctx.orgId(), List.of(receipt));
        }
    }

    /** Admins: an approved expense was reimbursed or paid; its person hears about it. */
    @Transactional
    public ExpenseView markPaid(String slug, UUID id) {
        OrgContext ctx = access.requireManager(slug);
        Expense e = lock(ctx, id);
        if (!Expense.APPROVED.equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an approved expense can be marked paid", List.of());
        }
        e.setStatus(Expense.PAID);
        e.setPaidAt(clock.instant());
        e.setPaidBy(ctx.userId());
        e.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.paid",
                Map.of("amount", e.getAmount().toPlainString()));
        notifications.send(ctx.orgId(), List.of(e.getUserId()), ctx.userId(), "business.expense.paid",
                Text.of("Expense paid: {title}", Map.of("title", titles.title(e))), null, link(ctx, e.getId()));
        return view(ctx, e);
    }

    // ── Receipt ──────────────────────────────────────────────────────────────────────

    /** Adds or replaces the receipt (a photo or PDF), while the expense can still be edited. */
    @Transactional
    public ExpenseView attachReceipt(String slug, UUID id, MultipartFile upload) {
        OrgContext ctx = access.require(slug);
        Expense e = lock(ctx, id);
        if (!editable(ctx, e, approvalStatus(ctx, e)) && !(Expense.PENDING.equals(e.getStatus()) && BusinessAccess.owns(ctx, e.getUserId()))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The receipt can't be changed any more", List.of());
        }
        StoredFile f = fileService.store(ctx.orgId(), ctx.userId(), upload);
        UUID old = e.getReceiptFileId();
        e.setReceiptFileId(f.getId());
        e.setUpdatedAt(clock.instant());
        expenses.saveAndFlush(e);
        if (old != null) {
            fileService.discard(ctx.orgId(), List.of(old));
        }
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.receipt",
                Map.of("name", f.getName()));
        return view(ctx, e);
    }

    @Transactional
    public ExpenseView removeReceipt(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Expense e = lock(ctx, id);
        if (!editable(ctx, e, approvalStatus(ctx, e))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The receipt can't be changed any more", List.of());
        }
        UUID old = e.getReceiptFileId();
        if (old != null) {
            e.setReceiptFileId(null);
            e.setUpdatedAt(clock.instant());
            expenses.saveAndFlush(e);
            fileService.discard(ctx.orgId(), List.of(old));
        }
        return view(ctx, e);
    }

    /** The receipt file, for the expense's person and Admins. */
    @Transactional(readOnly = true)
    public StoredFile receipt(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Expense e = visible(ctx, id);
        if (e.getReceiptFileId() == null) {
            throw BusinessException.notFound();
        }
        return files.findById(e.getReceiptFileId()).filter(f -> f.getOrgId().equals(ctx.orgId()))
                .orElseThrow(BusinessException::notFound);
    }

    /** The approval engine closed an expense's approval: settle the expense. */
    @EventListener
    @Transactional
    public void onApprovalClosed(ApprovalClosed ev) {
        if (!BusinessAccess.MODULE.equals(ev.module()) || !RECORD.equals(ev.recordType())) {
            return;
        }
        expenses.findByIdAndOrgId(ev.recordId(), ev.ctx().orgId())
                .filter(e -> Expense.PENDING.equals(e.getStatus()) && ev.requestId().equals(e.getApprovalId()))
                .ifPresent(e -> {
                    switch (ev.status()) {
                        case "APPROVED" -> settle(e, Expense.APPROVED);
                        case "REJECTED" -> settle(e, Expense.REJECTED);
                        default -> {
                            e.setStatus(Expense.DRAFT);
                            e.setUpdatedAt(clock.instant());
                        }
                    }
                });
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    Expense visible(OrgContext ctx, UUID id) {
        Expense e = expenses.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!BusinessAccess.owns(ctx, e.getUserId())) {
            throw BusinessException.notFound();
        }
        return e;
    }

    static String link(OrgContext ctx, UUID id) {
        return "/" + ctx.slug() + "/business/expenses/" + id;
    }

    private Expense lock(OrgContext ctx, UUID id) {
        Expense e = expenses.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
        if (!BusinessAccess.owns(ctx, e.getUserId())) {
            throw BusinessException.notFound();
        }
        return e;
    }

    private void submit(OrgContext ctx, Expense e) {
        e.setStatus(Expense.PENDING);
        e.setUpdatedAt(clock.instant());
        expenses.saveAndFlush(e);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.submitted",
                Map.of("amount", e.getAmount().toPlainString()));
        if (ctx.isAdmin() && noOtherAdmin(ctx)) {
            settle(e, Expense.APPROVED);
            activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, e.getId(), ctx.userId(), "business.expense.self_approved",
                    Map.of("amount", e.getAmount().toPlainString()));
            return;
        }
        UUID workflow = workflows.ensure(ctx, BusinessAccess.MODULE, WORKFLOW, new StepInput("ORG_ROLE", null, "ADMIN"));
        ApprovalDetail d = approvals.submit(ctx.slug(), new SubmitRequest(workflow, BusinessAccess.MODULE, RECORD, e.getId(),
                e.getNotes()));
        e.setApprovalId(d.summary().id());
    }

    private boolean noOtherAdmin(OrgContext ctx) {
        return memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .noneMatch(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole().isAdmin() && !m.getUserId().equals(ctx.userId()));
    }

    private void settle(Expense e, String status) {
        e.setStatus(status);
        e.setDecidedAt(clock.instant());
        e.setUpdatedAt(clock.instant());
    }

    private String approvalStatus(OrgContext ctx, Expense e) {
        return e.getApprovalId() == null ? null : approvals.statuses(ctx.orgId(), List.of(e.getApprovalId())).get(e.getApprovalId());
    }

    private static boolean editable(OrgContext ctx, Expense e, String approvalStatus) {
        return BusinessAccess.owns(ctx, e.getUserId()) && (Expense.DRAFT.equals(e.getStatus())
                || (Expense.PENDING.equals(e.getStatus()) && "CHANGES_REQUESTED".equals(approvalStatus)));
    }

    private void apply(OrgContext ctx, Expense e, ExpenseRequest in, UUID currentCategory) {
        ExpenseCategory c = categories.all(ctx).stream().filter(x -> x.getId().equals(in.categoryId())).findFirst()
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "categoryId", "Choose a category"));
        if (!c.isActive() && !c.getId().equals(currentCategory)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "categoryId", "This category is switched off");
        }
        BigDecimal amount = Totals.cents(in.amount());
        BigDecimal vat = in.vatAmount() == null ? null : Totals.cents(in.vatAmount());
        if (vat != null && vat.compareTo(amount) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "vatAmount", "The VAT can't be more than the amount");
        }
        e.setSpentOn(in.spentOn());
        e.setCategoryId(c.getId());
        e.setSupplier(BizPeople.clean(in.supplier()));
        e.setAmount(amount);
        e.setVatAmount(vat);
        e.setNotes(BizPeople.clean(in.notes()));
        e.setUpdatedAt(clock.instant());
    }

    ExpenseView view(OrgContext ctx, Expense e) {
        return views(ctx, List.of(e)).get(0);
    }

    private List<ExpenseView> views(OrgContext ctx, Collection<Expense> found) {
        Map<UUID, PersonRef> ps = people.of(found.stream().map(Expense::getUserId).toList());
        Map<UUID, CategoryView> cs = new HashMap<>();
        categories.all(ctx).forEach(c -> cs.put(c.getId(), ExpenseCategoryService.view(c)));
        Map<UUID, String> statuses = approvals.statuses(ctx.orgId(),
                found.stream().map(Expense::getApprovalId).filter(Objects::nonNull).toList());
        Map<UUID, StoredFile> receipts = new HashMap<>();
        List<UUID> fileIds = found.stream().map(Expense::getReceiptFileId).filter(Objects::nonNull).toList();
        if (!fileIds.isEmpty()) {
            files.findByIdInAndOrgId(fileIds, ctx.orgId()).forEach(f -> receipts.put(f.getId(), f));
        }
        return found.stream().map(e -> {
            String approval = e.getApprovalId() == null ? null : statuses.get(e.getApprovalId());
            StoredFile f = e.getReceiptFileId() == null ? null : receipts.get(e.getReceiptFileId());
            boolean edit = editable(ctx, e, approval);
            return new ExpenseView(e.getId(), ps.get(e.getUserId()), e.getSpentOn(), cs.get(e.getCategoryId()), e.getSupplier(),
                    e.getAmount(), e.getVatAmount(), e.getNotes(),
                    f == null ? null : new FileRef(f.getId(), f.getName(), f.getContentType(), f.getSizeBytes()),
                    e.getStatus(), e.getApprovalId(), approval, e.getPaidAt(), e.getCreatedAt(), edit,
                    Expense.DRAFT.equals(e.getStatus()) && e.getUserId().equals(ctx.userId()),
                    Expense.DRAFT.equals(e.getStatus()) && BusinessAccess.owns(ctx, e.getUserId()),
                    BusinessAccess.manages(ctx) && Expense.APPROVED.equals(e.getStatus()));
        }).toList();
    }
}
