package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.time.Clock;
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
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.CustomerDetail;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRequest;
import com.businessplatform.business.payload.BusinessPayloads.CustomerView;
import com.businessplatform.business.repository.CustomerRepository;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Customers: anyone in the module adds and edits them; Admins archive (and restore) them. An archived
 * customer keeps their documents but can't be put on a new quote or invoice.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    public static final String RECORD = "customer";
    static final int MAX_PAGE = 100;

    private final BusinessAccess access;
    private final Customers lookup;
    private final CustomerRepository customers;
    private final InvoiceRepository invoices;
    private final QuoteService quotes;
    private final InvoiceService invoiceService;
    private final ActivityService activity;
    private final Clock clock;

    /** Searches name, contact, email, phone and VAT number; archived ones only when asked. */
    @Transactional(readOnly = true)
    public PageResponse<CustomerView> list(String slug, String q, boolean archived, int page, int size) {
        OrgContext ctx = access.require(slug);
        Specification<Customer> spec = (c, query, cb) -> cb.and(cb.equal(c.get("orgId"), ctx.orgId()),
                cb.equal(c.get("archived"), archived));
        if (q != null && !q.isBlank()) {
            String like = like(q);
            spec = spec.and((c, query, cb) -> cb.or(cb.like(cb.lower(c.get("name")), like, '\\'),
                    cb.like(cb.lower(c.get("contactName")), like, '\\'), cb.like(cb.lower(c.get("email")), like, '\\'),
                    cb.like(c.get("phone"), like, '\\'), cb.like(cb.lower(c.get("taxId")), like, '\\')));
        }
        Page<Customer> found = customers.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE), Sort.by("name", "id")));
        Map<UUID, BigDecimal> owed = lookup.balances(ctx, found.getContent().stream().map(Customer::getId).toList());
        return PageResponse.of(found.map(c -> Customers.view(c, owed.getOrDefault(c.getId(), Totals.ZERO))));
    }

    /** The customer with what they owe (and how much is overdue), invoiced and paid, and their documents. */
    @Transactional(readOnly = true)
    public CustomerDetail get(String slug, UUID id) {
        OrgContext ctx = access.require(slug);
        Customer c = lookup.find(ctx, id);
        Object[] totals = invoices.customerTotals(ctx.orgId(), c.getId()).get(0);
        return new CustomerDetail(lookup.view(ctx, c),
                Customers.money(invoices.customerOverdue(ctx.orgId(), c.getId(), access.today())),
                Customers.money(totals[0]), Customers.money(totals[1]), quotes.forCustomer(ctx, c.getId()),
                invoiceService.forCustomer(ctx, c.getId()), BusinessAccess.manages(ctx));
    }

    @Transactional
    public CustomerView create(String slug, CustomerRequest in) {
        OrgContext ctx = access.require(slug);
        Customer c = new Customer();
        c.setId(Ids.next());
        c.setOrgId(ctx.orgId());
        c.setCreatedBy(ctx.userId());
        c.setCreatedAt(clock.instant());
        apply(c, in);
        customers.save(c);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, c.getId(), ctx.userId(), "business.customer.created",
                Map.of("name", c.getName()));
        return Customers.view(c, Totals.ZERO);
    }

    @Transactional
    public CustomerView update(String slug, UUID id, CustomerRequest in) {
        OrgContext ctx = access.require(slug);
        Customer c = lookup.find(ctx, id);
        apply(c, in);
        customers.save(c);
        activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, c.getId(), ctx.userId(), "business.customer.updated",
                Map.of("name", c.getName()));
        return lookup.view(ctx, c);
    }

    /** Admins: hides the customer from lists and pickers ({@code archived} false brings them back). */
    @Transactional
    public CustomerView archive(String slug, UUID id, boolean archived) {
        OrgContext ctx = access.requireManager(slug);
        Customer c = lookup.find(ctx, id);
        if (c.isArchived() != archived) {
            c.setArchived(archived);
            c.setUpdatedAt(clock.instant());
            customers.save(c);
            activity.record(ctx.orgId(), BusinessAccess.MODULE, RECORD, c.getId(), ctx.userId(),
                    archived ? "business.customer.archived" : "business.customer.restored", Map.of("name", c.getName()));
        }
        return lookup.view(ctx, c);
    }

    /** A LIKE pattern for {@code q}, lower case, with the wildcards in it escaped (escape character {@code \}). */
    static String like(String q) {
        return "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private void apply(Customer c, CustomerRequest in) {
        c.setKind(in.kind());
        c.setName(in.name().strip());
        c.setContactName(BizPeople.clean(in.contactName()));
        c.setEmail(BizPeople.clean(in.email()));
        c.setPhone(BizPeople.clean(in.phone()));
        c.setAddress(BizPeople.clean(in.address()));
        c.setTaxId(BizPeople.clean(in.taxId()));
        c.setNotes(BizPeople.clean(in.notes()));
        c.setUpdatedAt(clock.instant());
    }
}
