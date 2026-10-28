package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.BizSettings;
import com.businessplatform.business.domain.Customer;
import com.businessplatform.business.domain.DocLine;
import com.businessplatform.business.domain.Invoice;
import com.businessplatform.business.domain.Payment;
import com.businessplatform.business.domain.Quote;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.ActivityView;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRef;
import com.businessplatform.business.payload.BusinessPayloads.InvoiceRequest;
import com.businessplatform.business.payload.BusinessPayloads.InvoiceSummary;
import com.businessplatform.business.payload.BusinessPayloads.InvoiceView;
import com.businessplatform.business.payload.BusinessPayloads.PaymentRequest;
import com.businessplatform.business.payload.BusinessPayloads.PaymentView;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.business.payload.BusinessPayloads.VoidRequest;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.business.repository.PaymentRepository;
import com.businessplatform.business.repository.QuoteRepository;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.service.OrgContext;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Invoices. A DRAFT has no number and can be changed or deleted. Issuing gives it the organisation's next
 * number (INV-0001, taken under a row lock so two issues at once never share one) and fixes it: an issued
 * invoice is never edited — it is voided (Admins, with a reason, while it has no payments) and a new one is
 * issued. Payments (at most the balance) move it to PARTIALLY_PAID and PAID; Admins can remove a payment
 * recorded by mistake. Members do this on invoices they created; Admins on anyone's.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String RECORD = "invoice";
    static final int DUE_SOON_DAYS = 14;

    private final BusinessAccess access;
    private final BizSettingsService settings;
    private final Customers customers;
    private final Lines lines;
    private final BizPeople people;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final QuoteRepository quotes;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    /** {@code q}: number or customer name; {@code status}: a stored status or OVERDUE. Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(String slug, String q, String status, UUID customerId, int page, int size) {
        OrgContext ctx = access.require(slug);
        LocalDate today = access.today();
        Specification<Invoice> spec = (x, query, cb) -> cb.equal(x.get("orgId"), ctx.orgId());
        if (customerId != null) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("customerId"), customerId));
        }
        if ("OVERDUE".equals(status)) {
            spec = spec.and((x, query, cb) -> cb.and(x.get("status").in(Invoice.ISSUED, Invoice.PARTIALLY_PAID),
                    cb.lessThan(x.get("dueDate"), today)));
        } else if (status != null && !status.isBlank()) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = CustomerService.like(q);
            spec = spec.and((x, query, cb) -> {
                Subquery<UUID> named = query.subquery(UUID.class);
                Root<Customer> c = named.from(Customer.class);
                named.select(c.get("id")).where(cb.equal(c.get("orgId"), ctx.orgId()), cb.like(cb.lower(c.get("name")), like, '\\'));
                return cb.or(cb.like(cb.lower(x.get("number")), like, '\\'), x.get("customerId").in(named));
            });
        }
        Page<Invoice> found = invoices.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), CustomerService.MAX_PAGE), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(new PageImpl<>(summaries(ctx, found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public InvoiceView get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        return view(ctx, find(ctx, id));
    }

    @Transactional
    public InvoiceView create(String slug, InvoiceRequest in) {
        OrgContext ctx = access.require(slug);
        Invoice x = blank(ctx);
        List<DocLine> ls = apply(ctx, x, in);
        invoices.saveAndFlush(x);
        lines.saveForInvoice(x.getId(), ls);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.created",
                Map.of("customer", customers.find(ctx, x.getCustomerId()).getName(), "total", x.getTotal().toPlainString()));
        return view(ctx, x);
    }

    @Transactional
    public InvoiceView update(String slug, UUID id, InvoiceRequest in) {
        OrgContext ctx = access.require(slug);
        Invoice x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        requireDraft(x);
        List<DocLine> ls = apply(ctx, x, in);
        lines.saveForInvoice(x.getId(), ls);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.updated",
                Map.of("total", x.getTotal().toPlainString()));
        return view(ctx, x);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Invoice x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        if (!Invoice.DRAFT.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft can be deleted; void an issued invoice instead", List.of());
        }
        // A quote converted into this draft can be converted again (biz_quote.invoice_id is set null).
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.deleted",
                Map.of("total", x.getTotal().toPlainString()));
        invoices.delete(x);
    }

    /** Numbers and fixes a draft; an empty issue date becomes today and an empty due date today + the payment terms. */
    @Transactional
    public InvoiceView issue(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Invoice x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        requireDraft(x);
        customers.usable(ctx, x.getCustomerId());
        if (x.getTotal().signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "An invoice needs a total above 0", List.of());
        }
        BizSettings s = settings.settings(ctx);
        if (x.getIssueDate() == null) {
            x.setIssueDate(access.today());
        }
        if (x.getDueDate() == null) {
            x.setDueDate(x.getIssueDate().plusDays(s.getPaymentTermsDays()));
        }
        if (x.getDueDate().isBefore(x.getIssueDate())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dueDate", "The due date is before the invoice date");
        }
        x.setNumber(settings.nextInvoiceNumber(ctx));
        x.setStatus(Invoice.ISSUED);
        x.setIssuedAt(clock.instant());
        x.setIssuedBy(ctx.userId());
        x.setUpdatedAt(clock.instant());
        invoices.saveAndFlush(x);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.issued",
                Map.of("number", x.getNumber(), "total", x.getTotal().toPlainString(), "due", x.getDueDate().toString()));
        return view(ctx, x);
    }

    /** Records money received: at most the balance; the invoice's creator hears when it is paid in full. */
    @Transactional
    public InvoiceView pay(String slug, UUID id, PaymentRequest in) {
        OrgContext ctx = access.require(slug);
        Invoice x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        if (!x.open()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Payments go on an issued invoice that isn't paid yet", List.of());
        }
        BigDecimal amount = Totals.cents(in.amount());
        if (amount.compareTo(x.balance()) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "amount", "This is more than the balance");
        }
        if (in.paidOn().isBefore(x.getIssueDate())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "paidOn", "This is before the invoice date");
        }
        Payment p = new Payment();
        p.setId(Ids.next());
        p.setOrgId(ctx.orgId());
        p.setInvoiceId(x.getId());
        p.setPaidOn(in.paidOn());
        p.setAmount(amount);
        p.setMethod(in.method());
        p.setReference(BizPeople.clean(in.reference()));
        p.setRecordedBy(ctx.userId());
        p.setCreatedAt(clock.instant());
        payments.save(p);
        x.setPaid(x.getPaid().add(amount));
        settle(x);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.payment",
                Map.of("number", x.getNumber(), "amount", amount.toPlainString(), "method", p.getMethod()));
        if (Invoice.PAID.equals(x.getStatus())) {
            activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.paid",
                    Map.of("number", x.getNumber()));
            notifications.send(ctx.orgId(), List.of(x.getCreatedBy()), ctx.userId(), "business.invoice.paid",
                    Text.of("Invoice paid: {number}", Map.of("number", x.getNumber())),
                    Text.raw(customers.find(ctx, x.getCustomerId()).getName()), link(ctx, x.getId()));
        }
        return view(ctx, x);
    }

    /** Admins: takes back a payment recorded by mistake (not on a void invoice). */
    @Transactional
    public InvoiceView removePayment(String slug, UUID id, UUID paymentId) {
        OrgContext ctx = access.requireManager(slug);
        Invoice x = lock(ctx, id);
        Payment p = payments.findByIdAndInvoiceId(paymentId, x.getId()).orElseThrow(BusinessException::notFound);
        if (Invoice.VOID.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is void", List.of());
        }
        payments.delete(p);
        x.setPaid(x.getPaid().subtract(p.getAmount()));
        settle(x);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.payment_removed",
                Map.of("number", x.getNumber(), "amount", p.getAmount().toPlainString()));
        return view(ctx, x);
    }

    /** Admins: cancels an issued invoice that has no payments; its number stays used. */
    @Transactional
    public InvoiceView voidInvoice(String slug, UUID id, VoidRequest in) {
        OrgContext ctx = access.requireManager(slug);
        Invoice x = lock(ctx, id);
        if (Invoice.DRAFT.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A draft isn't issued; delete it instead", List.of());
        }
        if (Invoice.VOID.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is void", List.of());
        }
        if (x.getPaid().signum() > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Remove its payments before voiding this invoice", List.of());
        }
        x.setStatus(Invoice.VOID);
        x.setVoidReason(in.reason().strip());
        x.setVoidedAt(clock.instant());
        x.setVoidedBy(ctx.userId());
        x.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.voided",
                Map.of("number", x.getNumber(), "reason", x.getVoidReason()));
        return view(ctx, x);
    }

    /** What happened to the invoice, oldest first. */
    @Transactional(readOnly = true)
    public List<ActivityView> history(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Invoice x = find(ctx, id);
        List<Activity> as = activity.forRecord(ctx.orgId(), RECORD, x.getId());
        Map<UUID, PersonRef> ps = people.of(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityView(a.getId(), ps.get(a.getActorId()), a.getVerb(), a.getData(), a.getCreatedAt()))
                .toList();
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** A new draft from a quote (its customer, VAT, lines and notes); returns its id. */
    @Transactional
    UUID fromQuote(OrgContext ctx, Quote q, List<DocLine> from) {
        Invoice x = blank(ctx);
        x.setCustomerId(q.getCustomerId());
        x.setVatEnabled(q.isVatEnabled());
        x.setVatRate(q.getVatRate());
        x.setNotes(q.getNotes());
        x.setQuoteId(q.getId());
        List<DocLine> ls = Lines.copy(from);
        Totals.document(x, ls);
        invoices.saveAndFlush(x);
        lines.saveForInvoice(x.getId(), ls);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.invoice.created",
                Map.of("customer", customers.find(ctx, x.getCustomerId()).getName(), "total", x.getTotal().toPlainString(),
                        "quote", q.getNumber()));
        return x.getId();
    }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> forCustomer(OrgContext ctx, UUID customerId) {
        return summaries(ctx, invoices.findTop50ByOrgIdAndCustomerIdOrderByCreatedAtDesc(ctx.orgId(), customerId));
    }

    List<InvoiceSummary> summaries(OrgContext ctx, Collection<Invoice> found) {
        Map<UUID, CustomerRef> cs = customers.refs(ctx, found.stream().map(Invoice::getCustomerId).toList());
        Map<UUID, PersonRef> ps = people.of(found.stream().map(Invoice::getCreatedBy).toList());
        LocalDate today = access.today();
        return found.stream().map(x -> new InvoiceSummary(x.getId(), x.getNumber(), cs.get(x.getCustomerId()), x.getStatus(),
                x.overdue(today), x.getIssueDate(), x.getDueDate(), x.getTotal(), x.getPaid(), x.balance(),
                ps.get(x.getCreatedBy()))).toList();
    }

    static String link(OrgContext ctx, UUID id) {
        return "/" + ctx.slug() + "/business/invoices/" + id;
    }

    /** "INV-0003" or, for a draft, "Draft invoice · Customer". */
    static String title(Invoice x, String customer) {
        return (x.getNumber() == null ? "Draft invoice" : x.getNumber()) + " · " + customer;
    }

    InvoiceView view(OrgContext ctx, Invoice x) {
        Customer c = customers.find(ctx, x.getCustomerId());
        List<Payment> ps = payments.findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(x.getId());
        Map<UUID, PersonRef> who = people.of(Stream.concat(Stream.of(x.getCreatedBy()), ps.stream().map(Payment::getRecordedBy)).toList());
        boolean mine = BusinessAccess.owns(ctx, x.getCreatedBy());
        boolean draft = Invoice.DRAFT.equals(x.getStatus());
        String quoteNumber = x.getQuoteId() == null ? null : quotes.findById(x.getQuoteId()).map(Quote::getNumber).orElse(null);
        return new InvoiceView(x.getId(), x.getNumber(), customers.view(ctx, c), x.getStatus(), x.overdue(access.today()),
                x.getIssueDate(), x.getDueDate(), x.isVatEnabled(), x.getVatRate(), Lines.views(lines.ofInvoice(x.getId())),
                x.getSubtotal(), x.getDiscountTotal(), x.getVatTotal(), x.getTotal(), x.getPaid(), x.balance(), x.getNotes(),
                x.getQuoteId(), quoteNumber,
                ps.stream().map(p -> new PaymentView(p.getId(), p.getPaidOn(), p.getAmount(), p.getMethod(), p.getReference(),
                        who.get(p.getRecordedBy()), p.getCreatedAt())).toList(),
                x.getVoidReason(), x.getVoidedAt(), who.get(x.getCreatedBy()), x.getCreatedAt(), x.getIssuedAt(),
                mine && draft, mine && draft, mine && x.open(),
                BusinessAccess.manages(ctx) && x.open() && x.getPaid().signum() == 0, mine && draft,
                BusinessAccess.manages(ctx) && !Invoice.VOID.equals(x.getStatus()) && !ps.isEmpty());
    }

    private Invoice blank(OrgContext ctx) {
        Invoice x = new Invoice();
        x.setId(Ids.next());
        x.setOrgId(ctx.orgId());
        x.setStatus(Invoice.DRAFT);
        x.setVatRate(Totals.VAT_RATE);
        x.setPaid(Totals.ZERO);
        x.setCreatedBy(ctx.userId());
        x.setCreatedAt(clock.instant());
        x.setUpdatedAt(x.getCreatedAt());
        return x;
    }

    private Invoice find(OrgContext ctx, UUID id) {
        return invoices.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Invoice lock(OrgContext ctx, UUID id) {
        return invoices.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private static void requireDraft(Invoice x) {
        if (!Invoice.DRAFT.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "An issued invoice can't be changed; void it and issue a new one", List.of());
        }
    }

    /** PAID when nothing is left, PARTIALLY_PAID when some is, ISSUED when nothing is paid. */
    private void settle(Invoice x) {
        x.setStatus(x.getPaid().compareTo(x.getTotal()) >= 0 ? Invoice.PAID
                : x.getPaid().signum() > 0 ? Invoice.PARTIALLY_PAID : Invoice.ISSUED);
        x.setUpdatedAt(clock.instant());
    }

    /** Sets a draft's fields and totals from {@code in}; returns its new lines (not saved). */
    private List<DocLine> apply(OrgContext ctx, Invoice x, InvoiceRequest in) {
        Customer c = customers.usable(ctx, in.customerId());
        if (in.issueDate() != null && in.dueDate() != null && in.dueDate().isBefore(in.issueDate())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dueDate", "The due date is before the invoice date");
        }
        x.setCustomerId(c.getId());
        x.setIssueDate(in.issueDate());
        x.setDueDate(in.dueDate());
        x.setVatEnabled(in.vatEnabled() == null ? settings.settings(ctx).isVatDefault() : in.vatEnabled());
        x.setNotes(BizPeople.clean(in.notes()));
        x.setUpdatedAt(clock.instant());
        List<DocLine> ls = lines.build(ctx, in.lines());
        Totals.document(x, ls);
        return ls;
    }
}
