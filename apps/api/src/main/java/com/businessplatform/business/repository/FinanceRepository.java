package com.businessplatform.business.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * The finance reports' sums, done by PostgreSQL. Sales are issued invoices (not drafts or void) by invoice date;
 * money collected is payments by the day paid; expenses are approved or paid ones by the day spent.
 */
@Repository
@RequiredArgsConstructor
public class FinanceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public record MonthRow(LocalDate month, BigDecimal amount) {
    }

    public record NamedTotal(UUID id, String name, String nameKm, BigDecimal total, BigDecimal paid, long count) {
    }

    /** One open invoice's unpaid amount and how many days past its due date it is (negative = not due yet). */
    public record Open(UUID customerId, String customer, BigDecimal balance, long daysLate) {
    }

    private MapSqlParameterSource range(UUID orgId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("org", orgId).addValue("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
    }

    public List<MonthRow> salesPerMonth(UUID orgId, LocalDate from, LocalDate to) {
        return months("SELECT date_trunc('month', issue_date)::date AS m, SUM(total) AS a FROM biz_invoice WHERE org_id = :org"
                + " AND status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID') AND issue_date BETWEEN :from AND :to GROUP BY 1", range(orgId, from, to));
    }

    public List<MonthRow> collectedPerMonth(UUID orgId, LocalDate from, LocalDate to) {
        return months("SELECT date_trunc('month', p.paid_on)::date AS m, SUM(p.amount) AS a FROM biz_payment p"
                + " JOIN biz_invoice i ON i.id = p.invoice_id WHERE p.org_id = :org AND i.status <> 'VOID'"
                + " AND p.paid_on BETWEEN :from AND :to GROUP BY 1", range(orgId, from, to));
    }

    public List<MonthRow> expensesPerMonth(UUID orgId, LocalDate from, LocalDate to) {
        return months("SELECT date_trunc('month', spent_on)::date AS m, SUM(amount) AS a FROM biz_expense WHERE org_id = :org"
                + " AND status IN ('APPROVED', 'PAID') AND spent_on BETWEEN :from AND :to GROUP BY 1", range(orgId, from, to));
    }

    public List<NamedTotal> salesByCustomer(UUID orgId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT c.id, c.name, SUM(i.total) AS total, SUM(i.paid) AS paid, COUNT(*) AS n FROM biz_invoice i"
                + " JOIN customer c ON c.id = i.customer_id WHERE i.org_id = :org AND i.status IN ('ISSUED', 'PARTIALLY_PAID', 'PAID')"
                + " AND i.issue_date BETWEEN :from AND :to GROUP BY c.id, c.name ORDER BY total DESC, c.name", range(orgId, from, to),
                (rs, n) -> new NamedTotal(rs.getObject("id", UUID.class), rs.getString("name"), null, rs.getBigDecimal("total"),
                        rs.getBigDecimal("paid"), rs.getLong("n")));
    }

    public List<NamedTotal> expensesByCategory(UUID orgId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT c.id, c.name_en, c.name_km, SUM(e.amount) AS total, COUNT(*) AS n FROM biz_expense e"
                + " JOIN biz_expense_category c ON c.id = e.category_id WHERE e.org_id = :org AND e.status IN ('APPROVED', 'PAID')"
                + " AND e.spent_on BETWEEN :from AND :to GROUP BY c.id, c.name_en, c.name_km ORDER BY total DESC", range(orgId, from, to),
                (rs, n) -> new NamedTotal(rs.getObject("id", UUID.class), rs.getString("name_en"), rs.getString("name_km"),
                        rs.getBigDecimal("total"), null, rs.getLong("n")));
    }

    public List<Open> openInvoices(UUID orgId, LocalDate asOf) {
        return jdbc.query("SELECT c.id, c.name, i.total - i.paid AS balance, (:asOf - i.due_date) AS late FROM biz_invoice i"
                + " JOIN customer c ON c.id = i.customer_id WHERE i.org_id = :org AND i.status IN ('ISSUED', 'PARTIALLY_PAID')"
                + " AND i.total > i.paid", new MapSqlParameterSource("org", orgId).addValue("asOf", Date.valueOf(asOf)),
                (rs, n) -> new Open(rs.getObject("id", UUID.class), rs.getString("name"), rs.getBigDecimal("balance"), rs.getLong("late")));
    }

    private List<MonthRow> months(String sql, MapSqlParameterSource p) {
        return jdbc.query(sql, p, (rs, n) -> new MonthRow(rs.getDate("m").toLocalDate(), rs.getBigDecimal("a")));
    }
}
