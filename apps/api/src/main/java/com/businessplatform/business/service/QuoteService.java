package com.businessplatform.business.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.business.domain.Customer;
import com.businessplatform.business.domain.DocLine;
import com.businessplatform.business.domain.Quote;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRef;
import com.businessplatform.business.payload.BusinessPayloads.InvoiceView;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;
import com.businessplatform.business.payload.BusinessPayloads.QuoteRequest;
import com.businessplatform.business.payload.BusinessPayloads.QuoteSummary;
import com.businessplatform.business.payload.BusinessPayloads.QuoteView;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.business.repository.QuoteRepository;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.org.service.OrgContext;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Quotes. Numbered (Q-0001) when created, from the organisation's counter, so numbers never repeat. DRAFT and
 * SENT quotes can be changed; a SENT quote is ACCEPTED or DECLINED (an expired one can't be accepted) and an
 * accepted one becomes a draft invoice once ("Convert to invoice", which also accepts a SENT quote). Members
 * do this on their own quotes, Admins on anyone's.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    public static final String RECORD = "quote";

    private final BusinessAccess access;
    private final BizSettingsService settings;
    private final Customers customers;
    private final Lines lines;
    private final BizPeople people;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final InvoiceService invoiceService;
    private final ActivityService activity;
    private final Clock clock;

    /** {@code q}: number or customer name; {@code status}: a stored status or EXPIRED (SENT then excludes expired ones). */
    @Transactional(readOnly = true)
    public PageResponse<QuoteSummary> list(String slug, String q, String status, UUID customerId, int page, int size) {
        OrgContext ctx = access.require(slug);
        LocalDate today = access.today();
        Specification<Quote> spec = (x, query, cb) -> cb.equal(x.get("orgId"), ctx.orgId());
        if (customerId != null) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("customerId"), customerId));
        }
        if ("EXPIRED".equals(status)) {
            spec = spec.and((x, query, cb) -> cb.and(cb.equal(x.get("status"), Quote.SENT), cb.lessThan(x.get("validUntil"), today)));
        } else if (Quote.SENT.equals(status)) {
            spec = spec.and((x, query, cb) -> cb.and(cb.equal(x.get("status"), Quote.SENT),
                    cb.or(cb.isNull(x.get("validUntil")), cb.greaterThanOrEqualTo(x.get("validUntil"), today))));
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
        Page<Quote> found = quotes.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), CustomerService.MAX_PAGE), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return PageResponse.of(new org.springframework.data.domain.PageImpl<>(summaries(ctx, found.getContent()),
                found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public QuoteView get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        return view(ctx, find(ctx, id));
    }

    @Transactional
    public QuoteView create(String slug, QuoteRequest in) {
        OrgContext ctx = access.require(slug);
        Customer c = customers.usable(ctx, in.customerId());
        Quote x = new Quote();
        x.setId(Ids.next());
        x.setOrgId(ctx.orgId());
        x.setStatus(Quote.DRAFT);
        x.setVatRate(Totals.VAT_RATE);
        x.setCreatedBy(ctx.userId());
        x.setCreatedAt(clock.instant());
        List<DocLine> ls = apply(ctx, x, c, in);
        x.setNumber(settings.nextQuoteNumber(ctx));
        quotes.saveAndFlush(x);
        lines.saveForQuote(x.getId(), ls);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.quote.created",
                Map.of("number", x.getNumber(), "customer", c.getName(), "total", x.getTotal().toPlainString()));
        return view(ctx, x);
    }

    @Transactional
    public QuoteView update(String slug, UUID id, QuoteRequest in) {
        OrgContext ctx = access.require(slug);
        Quote x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        if (!editable(x)) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft or sent quote can be changed", List.of());
        }
        Customer c = customers.usable(ctx, in.customerId());
        List<DocLine> ls = apply(ctx, x, c, in);
        lines.saveForQuote(x.getId(), ls);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.quote.updated",
                Map.of("number", x.getNumber(), "total", x.getTotal().toPlainString()));
        return view(ctx, x);
    }

    /** A draft can be deleted (its number is not reused). */
    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Quote x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        if (!Quote.DRAFT.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft quote can be deleted", List.of());
        }
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.quote.deleted",
                Map.of("number", x.getNumber()));
        quotes.delete(x);
    }

    /** SENT (marked as given to the customer), ACCEPTED or DECLINED. */
    @Transactional
    public QuoteView mark(String slug, UUID id, String to) {
        OrgContext ctx = access.require(slug);
        Quote x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        String from = x.getStatus();
        switch (to) {
            case Quote.SENT -> {
                if (!Quote.DRAFT.equals(from)) {
                    throw new BusinessException(ErrorCode.INVALID_STATE, "Only a draft quote can be sent", List.of());
                }
            }
            case Quote.ACCEPTED, Quote.DECLINED -> {
                if (!Quote.SENT.equals(from)) {
                    throw new BusinessException(ErrorCode.INVALID_STATE, "Only a sent quote can be accepted or declined", List.of());
                }
                if (Quote.ACCEPTED.equals(to) && x.expired(access.today())) {
                    throw new BusinessException(ErrorCode.INVALID_STATE, "This quote has expired", List.of());
                }
            }
            default -> throw new IllegalArgumentException(to);
        }
        x.setStatus(to);
        x.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(),
                "business.quote." + to.toLowerCase(), Map.of("number", x.getNumber()));
        return view(ctx, x);
    }

    /** Makes a draft invoice with the quote's customer, lines and notes (once); a SENT quote becomes ACCEPTED. */
    @Transactional
    public InvoiceView convert(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Quote x = lock(ctx, id);
        BusinessAccess.requireOwner(ctx, x.getCreatedBy());
        if (x.getInvoiceId() != null) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This quote is already an invoice", List.of());
        }
        if (!Quote.ACCEPTED.equals(x.getStatus()) && !(Quote.SENT.equals(x.getStatus()) && !x.expired(access.today()))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a sent or accepted quote can become an invoice", List.of());
        }
        customers.usable(ctx, x.getCustomerId());
        x.setStatus(Quote.ACCEPTED);
        UUID invoiceId = invoiceService.fromQuote(ctx, x, lines.ofQuote(x.getId()));
        x.setInvoiceId(invoiceId);
        x.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, x.getId(), ctx.userId(), "business.quote.converted",
                Map.of("number", x.getNumber()));
        return invoiceService.view(ctx, invoices.findById(invoiceId).orElseThrow());
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QuoteSummary> forCustomer(OrgContext ctx, UUID customerId) {
        return summaries(ctx, quotes.findTop50ByOrgIdAndCustomerIdOrderByCreatedAtDesc(ctx.orgId(), customerId));
    }

    static String link(OrgContext ctx, UUID id) {
        return "/" + ctx.slug() + "/business/quotes/" + id;
    }

    private Quote find(OrgContext ctx, UUID id) {
        return quotes.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Quote lock(OrgContext ctx, UUID id) {
        return quotes.lock(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private static boolean editable(Quote x) {
        return (Quote.DRAFT.equals(x.getStatus()) || Quote.SENT.equals(x.getStatus())) && x.getInvoiceId() == null;
    }

    /** Sets the quote's fields and totals from {@code in}; returns its new lines (not saved). */
    private List<DocLine> apply(OrgContext ctx, Quote x, Customer c, QuoteRequest in) {
        if (in.validUntil() != null && in.validUntil().isBefore(in.issueDate())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "validUntil", "This is before the quote date");
        }
        x.setCustomerId(c.getId());
        x.setIssueDate(in.issueDate());
        x.setValidUntil(in.validUntil());
        x.setVatEnabled(in.vatEnabled() == null ? settings.settings(ctx).isVatDefault() : in.vatEnabled());
        x.setNotes(BizPeople.clean(in.notes()));
        x.setUpdatedAt(clock.instant());
        List<DocLine> ls = lines.build(ctx, in.lines());
        Totals.document(x, ls);
        return ls;
    }

    private List<QuoteSummary> summaries(OrgContext ctx, Collection<Quote> found) {
        Map<UUID, CustomerRef> cs = customers.refs(ctx, found.stream().map(Quote::getCustomerId).toList());
        Map<UUID, PersonRef> ps = people.of(found.stream().map(Quote::getCreatedBy).toList());
        LocalDate today = access.today();
        return found.stream().map(x -> new QuoteSummary(x.getId(), x.getNumber(), cs.get(x.getCustomerId()), x.getStatus(),
                x.expired(today), x.getIssueDate(), x.getValidUntil(), x.getTotal(), x.getInvoiceId(),
                ps.get(x.getCreatedBy()))).toList();
    }

    QuoteView view(OrgContext ctx, Quote x) {
        Map<UUID, CustomerRef> cs = customers.refs(ctx, List.of(x.getCustomerId()));
        boolean mine = BusinessAccess.owns(ctx, x.getCreatedBy());
        boolean expired = x.expired(access.today());
        String invoiceNumber = x.getInvoiceId() == null ? null
                : invoices.findById(x.getInvoiceId()).map(i -> i.getNumber()).orElse(null);
        Map<UUID, PersonRef> ps = new HashMap<>(people.of(List.of(x.getCreatedBy())));
        return new QuoteView(x.getId(), x.getNumber(), cs.get(x.getCustomerId()), x.getStatus(), expired,
                x.getIssueDate(), x.getValidUntil(), x.isVatEnabled(), x.getVatRate(), Lines.views(lines.ofQuote(x.getId())),
                x.getSubtotal(), x.getDiscountTotal(), x.getVatTotal(), x.getTotal(), x.getNotes(), x.getInvoiceId(),
                invoiceNumber, ps.get(x.getCreatedBy()), x.getCreatedAt(),
                mine && editable(x), mine && Quote.DRAFT.equals(x.getStatus()), mine && Quote.SENT.equals(x.getStatus()),
                mine && x.getInvoiceId() == null && (Quote.ACCEPTED.equals(x.getStatus()) || (Quote.SENT.equals(x.getStatus()) && !expired)),
                mine && Quote.DRAFT.equals(x.getStatus()));
    }
}
