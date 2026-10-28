package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Customer;
import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.CustomerRef;
import com.businessplatform.business.payload.BusinessPayloads.CustomerView;
import com.businessplatform.business.repository.CustomerRepository;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Looking up customers and what they owe, for the module's services. */
@Component
@RequiredArgsConstructor
public class Customers {

    private final CustomerRepository customers;
    private final InvoiceRepository invoices;

    @Transactional(readOnly = true)
    public Customer find(OrgContext ctx, UUID id) {
        return customers.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** A customer that may go on a new or changed document: in this organisation and not archived. */
    @Transactional(readOnly = true)
    public Customer usable(OrgContext ctx, UUID id) {
        Customer c = customers.findByIdAndOrgId(id, ctx.orgId())
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "customerId", "Choose a customer"));
        if (c.isArchived()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "customerId", "This customer is archived");
        }
        return c;
    }

    @Transactional(readOnly = true)
    public Map<UUID, CustomerRef> refs(OrgContext ctx, Collection<UUID> ids) {
        Map<UUID, CustomerRef> out = new HashMap<>();
        customers.findAllById(ids.stream().distinct().toList()).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                .forEach(c -> out.put(c.getId(), new CustomerRef(c.getId(), c.getName(), c.isArchived())));
        return out;
    }

    /** What each customer owes on issued, unpaid invoices (absent = nothing). */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> balances(OrgContext ctx, Collection<UUID> ids) {
        Map<UUID, BigDecimal> out = new HashMap<>();
        if (!ids.isEmpty()) {
            invoices.balances(ctx.orgId(), ids).forEach(r -> out.put((UUID) r[0], money(r[1])));
        }
        return out;
    }

    public CustomerView view(OrgContext ctx, Customer c) {
        return view(c, balances(ctx, List.of(c.getId())).getOrDefault(c.getId(), Totals.ZERO));
    }

    static BigDecimal money(Object o) {
        return o == null ? Totals.ZERO : Totals.cents(new BigDecimal(o.toString()));
    }

    static CustomerView view(Customer c, BigDecimal balance) {
        return new CustomerView(c.getId(), c.getKind(), c.getName(), c.getContactName(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getTaxId(), c.getNotes(), c.isArchived(), balance, c.getCreatedAt());
    }
}
