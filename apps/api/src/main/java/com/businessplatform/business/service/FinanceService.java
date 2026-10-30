package com.businessplatform.business.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Totals;
import com.businessplatform.business.payload.FinancePayloads.*;
import com.businessplatform.business.repository.FinanceRepository;
import com.businessplatform.business.repository.FinanceRepository.MonthRow;
import com.businessplatform.business.repository.FinanceRepository.Open;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * The finance reports for Owners and Admins: a year's sales, money collected, expenses and profit by month; what
 * customers owe by how late it is (as of today); sales by customer; expenses by category. Each also as CSV.
 */
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final BusinessAccess access;
    private final FinanceRepository finance;

    @Transactional(readOnly = true)
    public Report report(String slug, int year) {
        OrgContext ctx = access.requireManager(slug);
        LocalDate from = Year.of(year).atDay(1);
        LocalDate to = Year.of(year).atMonth(12).atEndOfMonth();
        Map<LocalDate, BigDecimal> sales = byMonth(finance.salesPerMonth(ctx.orgId(), from, to));
        Map<LocalDate, BigDecimal> collected = byMonth(finance.collectedPerMonth(ctx.orgId(), from, to));
        Map<LocalDate, BigDecimal> spent = byMonth(finance.expensesPerMonth(ctx.orgId(), from, to));
        List<MonthLine> months = new ArrayList<>();
        BigDecimal ts = BigDecimal.ZERO, tc = BigDecimal.ZERO, te = BigDecimal.ZERO;
        for (int m = 1; m <= 12; m++) {
            LocalDate month = LocalDate.of(year, m, 1);
            BigDecimal s = sales.getOrDefault(month, Totals.ZERO);
            BigDecimal c = collected.getOrDefault(month, Totals.ZERO);
            BigDecimal e = spent.getOrDefault(month, Totals.ZERO);
            months.add(new MonthLine(month, s, c, e, Customers.money(s.subtract(e))));
            ts = ts.add(s);
            tc = tc.add(c);
            te = te.add(e);
        }
        MonthLine totals = new MonthLine(from, Customers.money(ts), Customers.money(tc), Customers.money(te), Customers.money(ts.subtract(te)));

        List<Open> open = finance.openInvoices(ctx.orgId(), access.today());
        Map<UUID, List<Open>> byCustomer = open.stream().collect(Collectors.groupingBy(Open::customerId, LinkedHashMap::new, Collectors.toList()));
        List<CustomerAging> perCustomer = byCustomer.values().stream()
                .map(list -> new CustomerAging(list.get(0).customerId(), list.get(0).customer(), aging(list)))
                .sorted(Comparator.comparing((CustomerAging a) -> a.aging().total()).reversed()).toList();

        return new Report(year, months, totals, aging(open), perCustomer,
                finance.salesByCustomer(ctx.orgId(), from, to).stream()
                        .map(r -> new CustomerSales(r.id(), r.name(), r.count(), Customers.money(r.total()), Customers.money(r.paid()))).toList(),
                finance.expensesByCategory(ctx.orgId(), from, to).stream()
                        .map(r -> new CategoryTotal(r.id(), r.name(), r.nameKm(), r.count(), Customers.money(r.total()))).toList());
    }

    /** {@code which}: months, aging, customers or expenses. */
    @Transactional(readOnly = true)
    public String csv(String slug, int year, String which) {
        Report r = report(slug, year);
        StringBuilder out = new StringBuilder();
        switch (which) {
            case "months" -> {
                out.append("Month,Sales (USD),Collected (USD),Expenses (USD),Profit (USD)\n");
                r.months().forEach(m -> line(out, m.month().toString().substring(0, 7), m.sales(), m.collected(), m.expenses(), m.profit()));
                line(out, "Total", r.totals().sales(), r.totals().collected(), r.totals().expenses(), r.totals().profit());
            }
            case "aging" -> {
                out.append("Customer,Not yet due,1-30 days,31-60 days,61-90 days,Over 90 days,Total (USD)\n");
                r.agingByCustomer().forEach(a -> line(out, a.customer(), a.aging().current(), a.aging().days1to30(), a.aging().days31to60(),
                        a.aging().days61to90(), a.aging().over90(), a.aging().total()));
            }
            case "customers" -> {
                out.append("Customer,Invoices,Invoiced (USD),Paid (USD)\n");
                r.customers().forEach(c -> line(out, c.customer(), String.valueOf(c.invoices()), c.invoiced(), c.paid()));
            }
            case "expenses" -> {
                out.append("Category,Expenses,Total (USD)\n");
                r.categories().forEach(c -> line(out, c.nameEn(), String.valueOf(c.count()), c.total()));
            }
            default -> throw BusinessException.notFound();
        }
        return out.toString();
    }

    private static Aging aging(List<Open> open) {
        BigDecimal[] b = { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO };
        for (Open o : open) {
            int i = o.daysLate() <= 0 ? 0 : o.daysLate() <= 30 ? 1 : o.daysLate() <= 60 ? 2 : o.daysLate() <= 90 ? 3 : 4;
            b[i] = b[i].add(o.balance());
        }
        BigDecimal total = b[0].add(b[1]).add(b[2]).add(b[3]).add(b[4]);
        return new Aging(Customers.money(b[0]), Customers.money(b[1]), Customers.money(b[2]), Customers.money(b[3]),
                Customers.money(b[4]), Customers.money(total));
    }

    private static Map<LocalDate, BigDecimal> byMonth(List<MonthRow> rows) {
        Map<LocalDate, BigDecimal> out = new HashMap<>();
        rows.forEach(r -> out.put(r.month(), Customers.money(r.amount())));
        return out;
    }

    private static void line(StringBuilder out, Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            String v = cells[i] instanceof BigDecimal d ? d.toPlainString() : String.valueOf(cells[i]);
            String s = !v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0 && !(cells[i] instanceof BigDecimal) ? "'" + v : v;
            out.append(s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s);
        }
        out.append('\n');
    }
}
