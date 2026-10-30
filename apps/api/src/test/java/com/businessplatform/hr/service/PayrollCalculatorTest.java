package com.businessplatform.hr.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.businessplatform.hr.service.PayrollCalculator.Bracket;
import com.businessplatform.hr.service.PayrollCalculator.Input;
import com.businessplatform.hr.service.PayrollCalculator.Result;
import com.businessplatform.hr.service.PayrollCalculator.Rules;

/** Payslip arithmetic against examples worked by hand (Cambodia's default rates, 4,100 ៛ per dollar). */
class PayrollCalculatorTest {

    private static final Rules RULES = new Rules(List.of(new Bracket(1_500_000L, bd("0")), new Bracket(2_000_000L, bd("5")),
            new Bracket(8_500_000L, bd("10")), new Bracket(12_500_000L, bd("15")), new Bracket(null, bd("20"))),
            150_000, 400_000, 1_200_000, bd("2"), bd("5.4"), bd("150"));
    private static final BigDecimal RATE = bd("4100");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Input input(String salary, String unpaidDays, int overtimeMinutes, String allowances, String deductions,
            int dependants, boolean nssf) {
        return new Input(bd(salary), 22, 480, bd(unpaidDays), overtimeMinutes, bd(allowances), bd(deductions), dependants, nssf, RATE);
    }

    @Test
    void a_600_dollar_salary_with_one_dependant() {
        // 600 × 4,100 = 2,460,000 ៛; less 150,000 for the dependant = 2,310,000 taxable.
        // Tax: 500,000 × 5 % + 310,000 × 10 % = 25,000 + 31,000 = 56,000 ៛ = $13.66.
        // NSSF on the 1,200,000 ៛ cap: employee 2 % = 24,000 ៛ = $5.85; employer 5.4 % = 64,800 ៛ = $15.80.
        Result r = PayrollCalculator.calculate(input("600.00", "0", 0, "0", "0", 1, true), RULES);
        assertThat(r.gross()).isEqualByComparingTo("600.00");
        assertThat(r.taxableKhr()).isEqualTo(2_310_000);
        assertThat(r.taxKhr()).isEqualTo(56_000);
        assertThat(r.tax()).isEqualByComparingTo("13.66");
        assertThat(r.nssfEmployee()).isEqualByComparingTo("5.85");
        assertThat(r.nssfEmployer()).isEqualByComparingTo("15.80");
        assertThat(r.net()).isEqualByComparingTo("580.49");
    }

    @Test
    void below_the_tax_threshold_nothing_is_taxed_and_nssf_uses_the_floor() {
        // 90 × 4,100 = 369,000 ៛: under 1,500,000, so no tax; NSSF on the 400,000 floor: 2 % = 8,000 ៛ = $1.95.
        Result r = PayrollCalculator.calculate(input("90.00", "0", 0, "0", "0", 0, true), RULES);
        assertThat(r.taxKhr()).isZero();
        assertThat(r.nssfEmployee()).isEqualByComparingTo("1.95");
        assertThat(r.net()).isEqualByComparingTo("88.05");
    }

    @Test
    void unpaid_leave_overtime_and_adjustments() {
        // Daily 600 / 22 = 27.2727; one unpaid day = 27.27. Hourly 600 / 22 / 8 = 3.4091; 90 min at 150 % = 7.67.
        // Gross = 600 − 27.27 + 7.67 + 20 (allowance) = 600.40 → 2,461,640 ៛ − 0 dependants.
        // Tax: 25,000 + 461,640 × 10 % = 71,164 ៛ = $17.36; no NSSF; a 50 advance repaid after tax.
        Result r = PayrollCalculator.calculate(input("600.00", "1", 90, "20", "50", 0, false), RULES);
        assertThat(r.unpaidAmount()).isEqualByComparingTo("27.27");
        assertThat(r.overtimeAmount()).isEqualByComparingTo("7.67");
        assertThat(r.gross()).isEqualByComparingTo("600.40");
        assertThat(r.taxKhr()).isEqualTo(71_164);
        assertThat(r.tax()).isEqualByComparingTo("17.36");
        assertThat(r.nssfEmployee()).isEqualByComparingTo("0.00");
        assertThat(r.net()).isEqualByComparingTo("533.04");
    }

    @Test
    void the_top_bracket_applies_above_its_threshold() {
        // 20,000,000 ៛: 25,000 + 650,000 + 600,000 + 7,500,000 × 20 % = 2,775,000 ៛.
        assertThat(PayrollCalculator.tax(20_000_000, RULES.brackets())).isEqualTo(2_775_000);
        assertThat(PayrollCalculator.tax(1_500_000, RULES.brackets())).isZero();
    }
}
