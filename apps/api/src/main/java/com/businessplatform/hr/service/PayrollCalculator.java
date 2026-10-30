package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * One payslip's arithmetic, kept apart so it can be checked by hand. Salaries are USD; Tax on Salary and NSSF
 * are worked out in riel at the month's exchange rate and converted back.
 *
 * <ul>
 * <li>Unpaid leave: salary ÷ working days × unpaid days (never more than the salary).</li>
 * <li>Overtime: salary ÷ (working days × hours a day) × hours × the overtime rate (e.g. 150 %).</li>
 * <li>Gross: salary − unpaid leave + overtime + allowances (positive adjustments, taxable).</li>
 * <li>NSSF: on the gross in riel kept within the floor and cap; the employee's pension share is withheld, the
 * employer's shares are a cost.</li>
 * <li>Tax on Salary: progressive brackets on the gross in riel less the allowance per dependant.</li>
 * <li>Net: gross − tax − employee NSSF − deductions (negative adjustments, after tax).</li>
 * </ul>
 */
public final class PayrollCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private PayrollCalculator() {
    }

    /** {@code upToKhr} null = no upper limit (the last bracket). */
    public record Bracket(Long upToKhr, BigDecimal ratePct) {
    }

    public record Rules(List<Bracket> brackets, long dependantAllowanceKhr, long nssfFloorKhr, long nssfCapKhr,
            BigDecimal pensionEmployeePct, BigDecimal employerPct, BigDecimal overtimeRatePct) {
    }

    public record Input(BigDecimal salary, int workingDays, int dayMinutes, BigDecimal unpaidDays, int overtimeMinutes,
            BigDecimal allowances, BigDecimal deductions, int dependants, boolean nssf, BigDecimal exchangeRate) {
    }

    public record Result(BigDecimal unpaidAmount, BigDecimal overtimeAmount, BigDecimal gross, long taxableKhr,
            long taxKhr, BigDecimal tax, BigDecimal nssfEmployee, BigDecimal nssfEmployer, BigDecimal net) {
    }

    public static Result calculate(Input in, Rules rules) {
        BigDecimal salary = in.salary();
        BigDecimal unpaid = BigDecimal.ZERO;
        BigDecimal overtime = BigDecimal.ZERO;
        if (in.workingDays() > 0) {
            BigDecimal daily = salary.divide(BigDecimal.valueOf(in.workingDays()), 10, RoundingMode.HALF_UP);
            unpaid = money(daily.multiply(in.unpaidDays()).min(salary));
            if (in.dayMinutes() > 0 && in.overtimeMinutes() > 0) {
                BigDecimal hourly = daily.multiply(SIXTY).divide(BigDecimal.valueOf(in.dayMinutes()), 10, RoundingMode.HALF_UP);
                overtime = money(hourly.multiply(BigDecimal.valueOf(in.overtimeMinutes())).divide(SIXTY, 10, RoundingMode.HALF_UP)
                        .multiply(rules.overtimeRatePct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            }
        }
        BigDecimal gross = money(salary.subtract(unpaid).add(overtime).add(in.allowances()));
        BigDecimal rate = in.exchangeRate();
        long grossKhr = gross.multiply(rate).setScale(0, RoundingMode.HALF_UP).longValueExact();

        long nssfEmployeeKhr = 0;
        long nssfEmployerKhr = 0;
        if (in.nssf() && grossKhr > 0) {
            long base = Math.min(Math.max(grossKhr, rules.nssfFloorKhr()), rules.nssfCapKhr());
            nssfEmployeeKhr = percent(base, rules.pensionEmployeePct());
            nssfEmployerKhr = percent(base, rules.employerPct());
        }
        long taxable = Math.max(0, grossKhr - (long) in.dependants() * rules.dependantAllowanceKhr());
        long taxKhr = tax(taxable, rules.brackets());

        BigDecimal tax = toUsd(taxKhr, rate);
        BigDecimal nssfEmployee = toUsd(nssfEmployeeKhr, rate);
        BigDecimal nssfEmployer = toUsd(nssfEmployerKhr, rate);
        BigDecimal net = money(gross.subtract(tax).subtract(nssfEmployee).subtract(in.deductions()));
        return new Result(unpaid, overtime, gross, taxable, taxKhr, tax, nssfEmployee, nssfEmployer, net);
    }

    /** Progressive: each bracket's rate on the part of the amount that falls in it; whole riel. */
    static long tax(long taxableKhr, List<Bracket> brackets) {
        BigDecimal total = BigDecimal.ZERO;
        long lower = 0;
        for (Bracket b : brackets) {
            long upper = b.upToKhr() == null ? Long.MAX_VALUE : b.upToKhr();
            if (taxableKhr > lower) {
                long part = Math.min(taxableKhr, upper) - lower;
                total = total.add(BigDecimal.valueOf(part).multiply(b.ratePct()).divide(HUNDRED, 10, RoundingMode.HALF_UP));
            }
            if (b.upToKhr() == null || taxableKhr <= upper) {
                break;
            }
            lower = upper;
        }
        return total.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long percent(long khr, BigDecimal pct) {
        return BigDecimal.valueOf(khr).multiply(pct).divide(HUNDRED, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal toUsd(long khr, BigDecimal rate) {
        return BigDecimal.valueOf(khr).divide(rate, 2, RoundingMode.HALF_UP);
    }

    static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
