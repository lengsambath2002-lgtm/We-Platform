package com.businessplatform.business.payload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** General Business: the finance reports for a year (Owners and Admins). Money is USD. */
public final class FinancePayloads {

    private FinancePayloads() {
    }

    /** {@code profit}: sales − expenses (an accrual view); {@code collected}: payments received that month. */
    public record MonthLine(LocalDate month, BigDecimal sales, BigDecimal collected, BigDecimal expenses, BigDecimal profit) {
    }

    /** Unpaid amounts by how late they are; {@code current} is not yet due. */
    public record Aging(BigDecimal current, BigDecimal days1to30, BigDecimal days31to60, BigDecimal days61to90,
            BigDecimal over90, BigDecimal total) {
    }

    public record CustomerAging(UUID customerId, String customer, Aging aging) {
    }

    public record CustomerSales(UUID customerId, String customer, long invoices, BigDecimal invoiced, BigDecimal paid) {
    }

    public record CategoryTotal(UUID categoryId, String nameEn, String nameKm, long count, BigDecimal total) {
    }

    public record Report(int year, List<MonthLine> months, MonthLine totals, Aging aging, List<CustomerAging> agingByCustomer,
            List<CustomerSales> customers, List<CategoryTotal> categories) {
    }
}
