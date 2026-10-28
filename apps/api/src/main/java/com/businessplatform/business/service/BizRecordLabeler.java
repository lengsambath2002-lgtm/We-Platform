package com.businessplatform.business.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.business.repository.CustomerRepository;
import com.businessplatform.business.repository.ExpenseCategoryRepository;
import com.businessplatform.business.repository.ExpenseRepository;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.business.repository.ProductRepository;
import com.businessplatform.business.repository.QuoteRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of customers, products, quotes, invoices, expenses and categories for the audit log. */
@Component
@RequiredArgsConstructor
public class BizRecordLabeler implements RecordLabeler {

    private static final Set<String> TYPES = Set.of(CustomerService.RECORD, ProductService.RECORD, QuoteService.RECORD,
            InvoiceService.RECORD, ExpenseService.RECORD, ExpenseCategoryService.RECORD, BizSettingsService.RECORD);

    private final CustomerRepository customers;
    private final ProductRepository products;
    private final QuoteRepository quotes;
    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final ExpenseCategoryRepository categories;
    private final ExpenseTitles titles;

    @Override
    public boolean handles(String recordType) {
        return TYPES.contains(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/business";
        switch (recordType) {
            case CustomerService.RECORD -> customers.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getName(), base + "/customers/" + c.getId())));
            case ProductService.RECORD -> products.findAllById(ids).stream().filter(p -> p.getOrgId().equals(ctx.orgId()))
                    .forEach(p -> out.put(p.getId(), new Label(p.getNameEn(), base + "/products")));
            case QuoteService.RECORD -> {
                var found = quotes.findAllById(ids).stream().filter(q -> q.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, String> names = names(found.stream().map(q -> q.getCustomerId()).toList());
                found.forEach(q -> out.put(q.getId(), new Label(q.getNumber() + " · " + names.get(q.getCustomerId()),
                        QuoteService.link(ctx, q.getId()))));
            }
            case InvoiceService.RECORD -> {
                var found = invoices.findAllById(ids).stream().filter(i -> i.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, String> names = names(found.stream().map(i -> i.getCustomerId()).toList());
                found.forEach(i -> out.put(i.getId(), new Label(InvoiceService.title(i, names.get(i.getCustomerId())),
                        InvoiceService.link(ctx, i.getId()))));
            }
            case ExpenseService.RECORD -> expenses.findAllById(ids).stream().filter(e -> e.getOrgId().equals(ctx.orgId()))
                    .forEach(e -> out.put(e.getId(), new Label(titles.title(e), ExpenseService.link(ctx, e.getId()))));
            case ExpenseCategoryService.RECORD -> categories.findAllById(ids).stream().filter(c -> c.getOrgId().equals(ctx.orgId()))
                    .forEach(c -> out.put(c.getId(), new Label(c.getNameEn(), base + "/settings")));
            default -> ids.stream().filter(id -> id.equals(ctx.orgId()))
                    .forEach(id -> out.put(id, new Label(ctx.orgName(), base + "/settings")));
        }
        return out;
    }

    private Map<UUID, String> names(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        customers.findAllById(ids.stream().distinct().toList()).forEach(c -> out.put(c.getId(), c.getName()));
        return out;
    }
}
