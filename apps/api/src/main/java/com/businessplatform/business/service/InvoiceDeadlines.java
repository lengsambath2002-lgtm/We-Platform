package com.businessplatform.business.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Invoice;
import com.businessplatform.business.repository.CustomerRepository;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.calendar.DeadlineSource;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Due dates of issued invoices on the calendar of the people who run the business ({@code business.manage});
 * "only mine" keeps the invoices they created. Paid ones show as done. Nothing when the module is off.
 */
@Component
@RequiredArgsConstructor
public class InvoiceDeadlines implements DeadlineSource {

    private final BusinessAccess access;
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;

    @Override
    @Transactional(readOnly = true)
    public List<Deadline> deadlines(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        if (!access.usable(ctx) || !BusinessAccess.manages(ctx)) {
            return List.of();
        }
        List<Invoice> found = invoices.dueBetween(ctx.orgId(), from, to).stream()
                .filter(i -> !mineOnly || i.getCreatedBy().equals(ctx.userId())).toList();
        Map<UUID, String> names = new HashMap<>();
        customers.findAllById(found.stream().map(Invoice::getCustomerId).distinct().toList())
                .forEach(c -> names.put(c.getId(), c.getName()));
        return found.stream().map(i -> new Deadline(BusinessAccess.MODULE, i.getId(), i.getNumber(), names.get(i.getCustomerId()),
                i.getDueDate(), Invoice.PAID.equals(i.getStatus()) ? "DONE" : "TODO", InvoiceService.link(ctx, i.getId()),
                i.getCreatedBy().equals(ctx.userId()))).toList();
    }

    @Override
    public boolean canSeeProject(OrgContext ctx, UUID projectId) {
        return false;
    }
}
