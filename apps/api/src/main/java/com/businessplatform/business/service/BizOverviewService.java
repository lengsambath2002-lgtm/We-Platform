package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.BusinessPayloads.MonthTotals;
import com.businessplatform.business.payload.BusinessPayloads.Overview;
import com.businessplatform.business.repository.ExpenseRepository;
import com.businessplatform.business.repository.InvoiceRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The business home: what customers owe (and how much is overdue), this month's sales (invoices issued, by
 * invoice date) against approved expenses (by the day spent), the last {@value #MONTHS} months of both, recent
 * invoices, invoices due in the next {@value InvoiceService#DUE_SOON_DAYS} days and expenses waiting for a
 * decision. Expense totals are for Admins only.
 */
@Service
@RequiredArgsConstructor
public class BizOverviewService {

    static final int MONTHS = 6;

    private final BusinessAccess access;
    private final InvoiceRepository invoices;
    private final ExpenseRepository expenses;
    private final InvoiceService invoiceService;
    private final ExpenseService expenseService;

    @Transactional
    public Overview overview(String slug) {
        OrgContext ctx = access.require(slug);
        boolean manager = BusinessAccess.manages(ctx);
        LocalDate today = access.today();
        LocalDate thisMonth = today.withDayOfMonth(1);
        LocalDate first = thisMonth.minusMonths(MONTHS - 1);
        Object[] owed = invoices.receivable(ctx.orgId()).get(0);
        Object[] late = invoices.overdue(ctx.orgId(), today).get(0);
        Map<LocalDate, BigDecimal> sales = perMonth(invoices.salesPerMonth(ctx.orgId(), first, today));
        Map<LocalDate, BigDecimal> spent = manager ? perMonth(expenses.approvedPerMonth(ctx.orgId(), first, today)) : Map.of();
        List<MonthTotals> months = new ArrayList<>();
        for (LocalDate m = first; !m.isAfter(thisMonth); m = m.plusMonths(1)) {
            months.add(new MonthTotals(m, sales.getOrDefault(m, Totals.ZERO), manager ? spent.getOrDefault(m, Totals.ZERO) : null));
        }
        return new Overview(Customers.money(owed[1]), ((Number) owed[0]).intValue(), Customers.money(late[1]),
                ((Number) late[0]).intValue(), sales.getOrDefault(thisMonth, Totals.ZERO),
                manager ? spent.getOrDefault(thisMonth, Totals.ZERO) : null, months,
                invoiceService.summaries(ctx, invoices.findByOrgIdOrderByUpdatedAtDesc(ctx.orgId(), PageRequest.of(0, 5))),
                invoiceService.summaries(ctx, invoices.openDueBetween(ctx.orgId(), today, today.plusDays(InvoiceService.DUE_SOON_DAYS))),
                expenseService.waiting(ctx));
    }

    private static Map<LocalDate, BigDecimal> perMonth(List<Object[]> rows) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate m = r[0] instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) r[0];
            out.put(m, Customers.money(r[1]));
        }
        return out;
    }
}
