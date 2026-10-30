package com.businessplatform.ops.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.file.StoredFileRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.ops.domain.Sla;
import com.businessplatform.ops.domain.Ticket;
import com.businessplatform.ops.domain.TicketAttachment;
import com.businessplatform.ops.domain.TicketComment;
import com.businessplatform.ops.payload.OpsPayloads.ActivityView;
import com.businessplatform.ops.payload.OpsPayloads.CommentView;
import com.businessplatform.ops.payload.OpsPayloads.FileRef;
import com.businessplatform.ops.payload.OpsPayloads.PersonRef;
import com.businessplatform.ops.payload.OpsPayloads.Ref;
import com.businessplatform.ops.payload.OpsPayloads.TicketRequest;
import com.businessplatform.ops.payload.OpsPayloads.TicketView;
import com.businessplatform.ops.repository.TicketAttachmentRepository;
import com.businessplatform.ops.repository.TicketCommentRepository;
import com.businessplatform.ops.repository.TicketRepository;
import com.businessplatform.ops.repository.WorkOrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service requests and tickets (TCK-0001, from one atomic per-organisation counter). Dispatchers and Admins take
 * requests, triage, assign and close them; a technician sees and works the tickets assigned to them (in progress,
 * waiting, resolved), comments and adds files. Status flow: NEW → TRIAGED → IN_PROGRESS ⇄ WAITING → RESOLVED →
 * CLOSED, and RESOLVED / CLOSED reopen to IN_PROGRESS. The first move out of NEW, or the first comment, is the
 * response; SLA targets come from the priority's policy and breached / at risk is derived on every read.
 */
@Service
@RequiredArgsConstructor
public class TicketService {

    public static final String RECORD = "ticket";

    private static final Map<String, List<String>> NEXT = Map.of(
            Ticket.NEW, List.of(Ticket.TRIAGED, Ticket.IN_PROGRESS, Ticket.WAITING, Ticket.RESOLVED, Ticket.CLOSED),
            Ticket.TRIAGED, List.of(Ticket.IN_PROGRESS, Ticket.WAITING, Ticket.RESOLVED, Ticket.CLOSED),
            Ticket.IN_PROGRESS, List.of(Ticket.WAITING, Ticket.RESOLVED, Ticket.CLOSED),
            Ticket.WAITING, List.of(Ticket.IN_PROGRESS, Ticket.RESOLVED, Ticket.CLOSED),
            Ticket.RESOLVED, List.of(Ticket.CLOSED, Ticket.IN_PROGRESS),
            Ticket.CLOSED, List.of(Ticket.IN_PROGRESS));
    /** What a technician may move their own tickets to. */
    private static final Set<String> TECHNICIAN_MAY = Set.of(Ticket.IN_PROGRESS, Ticket.WAITING, Ticket.RESOLVED);

    private final OpsAccess access;
    private final OpsSettingsService settings;
    private final OpsCustomerService customers;
    private final AssetService assets;
    private final OpsViews views;
    private final OpsPeople people;
    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final TicketAttachmentRepository attachments;
    private final WorkOrderRepository workOrders;
    private final FileService fileService;
    private final StoredFileRepository files;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Read ─────────────────────────────────────────────────────────────────────────

    /**
     * Newest first. {@code status}: one status, or OPEN (not resolved or closed); {@code assignee}: "me", "none" or
     * a user id; {@code sla}: BREACHED or AT_RISK (open tickets only). Technicians only get their own.
     */
    @Transactional(readOnly = true)
    public PageResponse<com.businessplatform.ops.payload.OpsPayloads.TicketSummary> list(String slug, String q, String status,
            String priority, String category, String assignee, String sla, UUID customerId, int page, int size) {
        OpsAccess.Who w = access.require(slug);
        Instant now = clock.instant();
        Specification<Ticket> spec = (t, query, cb) -> cb.equal(t.get("orgId"), w.orgId());
        if (!w.manager() || "me".equals(assignee)) {
            spec = spec.and((t, query, cb) -> cb.equal(t.get("assigneeId"), w.userId()));
        } else if ("none".equals(assignee)) {
            spec = spec.and((t, query, cb) -> cb.isNull(t.get("assigneeId")));
        } else if (assignee != null && !assignee.isBlank()) {
            UUID who = uuid(assignee);
            spec = spec.and((t, query, cb) -> cb.equal(t.get("assigneeId"), who));
        }
        if ("OPEN".equals(status) || sla != null && !sla.isBlank()) {
            spec = spec.and((t, query, cb) -> t.get("status").in(Ticket.OPEN));
        }
        if (status != null && !status.isBlank() && !"OPEN".equals(status)) {
            spec = spec.and((t, query, cb) -> cb.equal(t.get("status"), status));
        }
        if (priority != null && !priority.isBlank()) {
            spec = spec.and((t, query, cb) -> cb.equal(t.get("priority"), priority));
        }
        if (category != null && !category.isBlank()) {
            spec = spec.and((t, query, cb) -> cb.equal(t.get("category"), category));
        }
        if (customerId != null) {
            spec = spec.and((t, query, cb) -> cb.equal(t.get("customerId"), customerId));
        }
        if (Sla.BREACHED.equals(sla)) {
            spec = spec.and((t, query, cb) -> cb.or(cb.and(cb.isNull(t.get("respondedAt")), cb.lessThan(t.get("responseDueAt"), now)),
                    cb.lessThan(t.get("resolutionDueAt"), now)));
        } else if (Sla.AT_RISK.equals(sla)) {
            spec = spec.and((t, query, cb) -> cb.and(
                    cb.not(cb.or(cb.and(cb.isNull(t.get("respondedAt")), cb.lessThan(t.get("responseDueAt"), now)),
                            cb.lessThan(t.get("resolutionDueAt"), now))),
                    cb.or(cb.and(cb.isNull(t.get("respondedAt")), cb.lessThanOrEqualTo(t.get("responseWarnAt"), now)),
                            cb.lessThanOrEqualTo(t.get("resolutionWarnAt"), now))));
        }
        if (q != null && !q.isBlank()) {
            String like = OpsPeople.like(q);
            spec = spec.and((t, query, cb) -> cb.or(cb.like(cb.lower(t.get("number")), like, '\\'),
                    cb.like(cb.lower(t.get("title")), like, '\\'), cb.like(cb.lower(t.get("requesterName")), like, '\\'),
                    cb.like(t.get("requesterPhone"), like, '\\')));
        }
        Page<Ticket> found = tickets.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), OpsCustomerService.MAX_PAGE),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(new PageImpl<>(views.tickets(w.orgId(), found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public TicketView get(String slug, UUID id) {
        OpsAccess.Who w = access.require(slug);
        return view(w, visible(w, id));
    }

    // ── Change ───────────────────────────────────────────────────────────────────────

    @Transactional
    public TicketView create(String slug, TicketRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        Instant now = clock.instant();
        Ticket t = new Ticket();
        t.setId(Ids.next());
        t.setOrgId(w.orgId());
        t.setStatus(Ticket.NEW);
        t.setCreatedBy(w.userId());
        t.setCreatedAt(now);
        apply(w, t, in, false);
        Sla.apply(t, settings.policy(w.orgId(), t.getPriority()), now);
        t.setNumber(settings.nextNumber(w.orgId(), "TICKET"));
        if (in.assigneeId() != null) {
            requireWorker(w, in.assigneeId());
            t.setAssigneeId(in.assigneeId());
        }
        tickets.saveAndFlush(t);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.created",
                Map.of("number", t.getNumber(), "title", t.getTitle(), "priority", t.getPriority()));
        if (t.getAssigneeId() != null) {
            notifyAssigned(w, t);
        }
        return view(w, t);
    }

    @Transactional
    public TicketView update(String slug, UUID id, TicketRequest in) {
        OpsAccess.Who w = access.requireManager(slug);
        Ticket t = lock(w, id);
        String priority = t.getPriority();
        apply(w, t, in, true);
        Map<String, Object> data = new HashMap<>(Map.of("number", t.getNumber()));
        if (!priority.equals(t.getPriority())) {
            // New targets from the new priority's policy, counted from when the ticket came in.
            Sla.apply(t, settings.policy(w.orgId(), t.getPriority()), t.getCreatedAt());
            data.put("priority", Map.of("from", priority, "to", t.getPriority()));
        }
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.updated", data);
        return view(w, t);
    }

    @Transactional
    public TicketView status(String slug, UUID id, String to) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = lock(w, id);
        if (!next(w, t).contains(to)) {
            if (!w.manager() && NEXT.getOrDefault(t.getStatus(), List.of()).contains(to)) {
                throw BusinessException.forbidden();
            }
            throw new BusinessException(ErrorCode.INVALID_STATE, "A ticket can't move to this status from here", List.of());
        }
        String from = t.getStatus();
        Instant now = clock.instant();
        boolean reopen = Ticket.IN_PROGRESS.equals(to) && (Ticket.RESOLVED.equals(from) || Ticket.CLOSED.equals(from));
        t.setStatus(to);
        if (t.getRespondedAt() == null) {
            t.setRespondedAt(now);
        }
        if (reopen) {
            t.setResolvedAt(null);
            t.setClosedAt(null);
        }
        if (Ticket.RESOLVED.equals(to)) {
            t.setResolvedAt(now);
        }
        if (Ticket.CLOSED.equals(to)) {
            t.setClosedAt(now);
            if (t.getResolvedAt() == null) {
                t.setResolvedAt(now);
            }
        }
        t.setUpdatedAt(now);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(),
                reopen ? "service.ticket.reopened" : "service.ticket.status", Map.of("number", t.getNumber(), "from", from, "to", to));
        return view(w, t);
    }

    @Transactional
    public TicketView assign(String slug, UUID id, UUID assigneeId) {
        OpsAccess.Who w = access.requireManager(slug);
        Ticket t = lock(w, id);
        if (assigneeId != null) {
            requireWorker(w, assigneeId);
        }
        UUID before = t.getAssigneeId();
        t.setAssigneeId(assigneeId);
        t.setUpdatedAt(clock.instant());
        PersonRef p = people.one(assigneeId);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(),
                assigneeId == null ? "service.ticket.unassigned" : "service.ticket.assigned",
                Map.of("number", t.getNumber(), "name", p == null ? "" : p.name()));
        if (assigneeId != null && !assigneeId.equals(before)) {
            notifyAssigned(w, t);
        }
        return view(w, t);
    }

    // ── Comments and files ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CommentView> comments(String slug, UUID id) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = visible(w, id);
        List<TicketComment> cs = comments.findByOrgIdAndTicketIdOrderByCreatedAt(w.orgId(), t.getId());
        Map<UUID, PersonRef> ps = people.of(cs.stream().map(TicketComment::getAuthorId).toList());
        return cs.stream().map(c -> new CommentView(c.getId(), ps.get(c.getAuthorId()), c.getBody(), c.getCreatedAt())).toList();
    }

    /** Adds a comment; the assignee and the person who took the request hear about it. */
    @Transactional
    public CommentView comment(String slug, UUID id, String body) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = lock(w, id);
        Instant now = clock.instant();
        TicketComment c = new TicketComment();
        c.setId(Ids.next());
        c.setOrgId(w.orgId());
        c.setTicketId(t.getId());
        c.setAuthorId(w.userId());
        c.setBody(body.strip());
        c.setCreatedAt(now);
        comments.save(c);
        if (t.getRespondedAt() == null) {
            t.setRespondedAt(now);
        }
        t.setUpdatedAt(now);
        String excerpt = c.getBody().length() > 120 ? c.getBody().substring(0, 120) + "…" : c.getBody();
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.commented",
                Map.of("number", t.getNumber(), "excerpt", excerpt));
        List<UUID> to = new ArrayList<>();
        if (t.getAssigneeId() != null) {
            to.add(t.getAssigneeId());
        }
        to.add(t.getCreatedBy());
        notifications.send(w.orgId(), to, w.userId(), "service.ticket.commented",
                Text.of("New comment on {number}", Map.of("number", t.getNumber())), Text.raw(excerpt), link(w.ctx().slug(), t.getId()));
        return new CommentView(c.getId(), people.one(w.userId()), c.getBody(), c.getCreatedAt());
    }

    @Transactional
    public TicketView attach(String slug, UUID id, MultipartFile upload) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = lock(w, id);
        StoredFile f = fileService.store(w.orgId(), w.userId(), upload);
        TicketAttachment a = new TicketAttachment();
        a.setId(Ids.next());
        a.setOrgId(w.orgId());
        a.setTicketId(t.getId());
        a.setFileId(f.getId());
        a.setAddedBy(w.userId());
        a.setCreatedAt(clock.instant());
        attachments.save(a);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.attached",
                Map.of("number", t.getNumber(), "name", f.getName()));
        return view(w, t);
    }

    /** The person who added it, or a dispatcher / Admin, removes a file. */
    @Transactional
    public TicketView detach(String slug, UUID id, UUID fileId) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = lock(w, id);
        TicketAttachment a = attachments.findByOrgIdAndTicketIdAndFileId(w.orgId(), t.getId(), fileId)
                .orElseThrow(BusinessException::notFound);
        if (!w.manager() && !a.getAddedBy().equals(w.userId())) {
            throw BusinessException.forbidden();
        }
        String name = files.findById(fileId).map(StoredFile::getName).orElse("");
        attachments.delete(a);
        attachments.flush();
        fileService.discard(w.orgId(), List.of(fileId));
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.detached",
                Map.of("number", t.getNumber(), "name", name));
        return view(w, t);
    }

    @Transactional(readOnly = true)
    public StoredFile file(String slug, UUID id, UUID fileId) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = visible(w, id);
        attachments.findByOrgIdAndTicketIdAndFileId(w.orgId(), t.getId(), fileId).orElseThrow(BusinessException::notFound);
        return files.findById(fileId).filter(f -> f.getOrgId().equals(w.orgId())).orElseThrow(BusinessException::notFound);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> history(String slug, UUID id) {
        OpsAccess.Who w = access.require(slug);
        Ticket t = visible(w, id);
        List<Activity> as = activity.forRecord(w.orgId(), RECORD, t.getId());
        Map<UUID, PersonRef> ps = people.of(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityView(a.getId(), a.getActorId() == null ? null : ps.get(a.getActorId()),
                a.getVerb(), a.getData(), a.getCreatedAt())).toList();
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** Visible: to dispatchers and Admins, and to the technician it is assigned to (NOT_FOUND otherwise). */
    Ticket visible(OpsAccess.Who w, UUID id) {
        Ticket t = tickets.findByIdAndOrgId(id, w.orgId()).orElseThrow(BusinessException::notFound);
        if (!w.manager() && !w.userId().equals(t.getAssigneeId())) {
            throw BusinessException.notFound();
        }
        return t;
    }

    /** A ticket gets its first work order: it is being worked on. */
    void workStarted(OpsAccess.Who w, Ticket t) {
        if (Ticket.NEW.equals(t.getStatus()) || Ticket.TRIAGED.equals(t.getStatus())) {
            String from = t.getStatus();
            t.setStatus(Ticket.IN_PROGRESS);
            if (t.getRespondedAt() == null) {
                t.setRespondedAt(clock.instant());
            }
            t.setUpdatedAt(clock.instant());
            activity.record(w.orgId(), OpsAccess.MODULE, RECORD, t.getId(), w.userId(), "service.ticket.status",
                    Map.of("number", t.getNumber(), "from", from, "to", Ticket.IN_PROGRESS));
        }
    }

    Ticket lockForWork(OpsAccess.Who w, UUID id) {
        return tickets.lock(id, w.orgId()).orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "ticketId",
                "Choose a ticket"));
    }

    public static String link(String slug, UUID id) {
        return "/" + slug + "/service/tickets/" + id;
    }

    private Ticket lock(OpsAccess.Who w, UUID id) {
        Ticket t = tickets.lock(id, w.orgId()).orElseThrow(BusinessException::notFound);
        if (!w.manager() && !w.userId().equals(t.getAssigneeId())) {
            throw BusinessException.notFound();
        }
        return t;
    }

    private List<String> next(OpsAccess.Who w, Ticket t) {
        List<String> all = NEXT.getOrDefault(t.getStatus(), List.of());
        if (w.manager()) {
            return all;
        }
        if (Ticket.CLOSED.equals(t.getStatus())) {
            return List.of();
        }
        return all.stream().filter(TECHNICIAN_MAY::contains).toList();
    }

    private void requireWorker(OpsAccess.Who w, UUID userId) {
        if (!access.works(w.orgId(), userId)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "assigneeId", "Choose someone on the service staff");
        }
    }

    private void apply(OpsAccess.Who w, Ticket t, TicketRequest in, boolean existing) {
        boolean sameCustomer = existing && in.customerId() != null && in.customerId().equals(t.getCustomerId());
        customers.checkLink(w.orgId(), in.customerId(), in.siteId(), sameCustomer);
        assets.checkLink(w.orgId(), in.assetId(), in.customerId());
        t.setTitle(in.title().strip());
        t.setDescription(OpsPeople.clean(in.description()));
        t.setCustomerId(in.customerId());
        t.setSiteId(in.siteId());
        t.setAssetId(in.assetId());
        t.setRequesterName(OpsPeople.clean(in.requesterName()));
        t.setRequesterPhone(OpsPeople.clean(in.requesterPhone()));
        t.setCategory(in.category());
        t.setPriority(in.priority());
        t.setUpdatedAt(clock.instant());
    }

    private void notifyAssigned(OpsAccess.Who w, Ticket t) {
        notifications.send(w.orgId(), List.of(t.getAssigneeId()), w.userId(), "service.ticket.assigned",
                Text.of("Ticket assigned to you: {number}", Map.of("number", t.getNumber())), Text.raw(t.getTitle()),
                link(w.ctx().slug(), t.getId()));
    }

    private TicketView view(OpsAccess.Who w, Ticket t) {
        Instant now = clock.instant();
        var summary = views.tickets(w.orgId(), List.of(t)).get(0);
        Map<UUID, Ref> sites = views.siteRefs(w.orgId(), java.util.Collections.singletonList(t.getSiteId()));
        Map<UUID, Ref> assetRefs = views.assetRefs(w.orgId(), java.util.Collections.singletonList(t.getAssetId()));
        List<TicketAttachment> as = attachments.findByOrgIdAndTicketIdOrderByCreatedAt(w.orgId(), t.getId());
        Map<UUID, StoredFile> fs = new HashMap<>();
        if (!as.isEmpty()) {
            files.findByIdInAndOrgId(as.stream().map(TicketAttachment::getFileId).toList(), w.orgId()).forEach(f -> fs.put(f.getId(), f));
        }
        Map<UUID, PersonRef> ps = people.of(java.util.stream.Stream.concat(as.stream().map(TicketAttachment::getAddedBy),
                java.util.stream.Stream.of(t.getCreatedBy())).toList());
        List<FileRef> refs = as.stream().filter(a -> fs.containsKey(a.getFileId())).map(a -> {
            StoredFile f = fs.get(a.getFileId());
            return new FileRef(f.getId(), f.getName(), f.getContentType(), f.getSizeBytes(), a.getCreatedAt(), ps.get(a.getAddedBy()));
        }).toList();
        var wos = workOrders.findByOrgIdAndTicketIdOrderByCreatedAt(w.orgId(), t.getId()).stream()
                .filter(x -> w.manager() || views.technicians(x.getId()).contains(w.userId())).toList();
        return new TicketView(summary, t.getDescription(), OpsViews.ref(sites, t.getSiteId()), OpsViews.ref(assetRefs, t.getAssetId()),
                t.getRequesterName(), t.getRequesterPhone(), ps.get(t.getCreatedBy()), Sla.response(t, now), Sla.resolution(t, now),
                t.getRespondedAt(), t.getResolvedAt(), t.getClosedAt(), next(w, t), refs, views.workOrders(w.orgId(), wos),
                w.manager(), w.manager(), w.manager() && t.isOpen());
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "assignee", "Choose someone on the service staff");
        }
    }
}
