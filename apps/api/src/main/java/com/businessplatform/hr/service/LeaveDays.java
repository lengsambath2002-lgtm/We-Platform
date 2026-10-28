package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Working days in a leave request: Monday to Friday count, weekends don't. A half first day (starts at
 * midday) or half last day (ends at midday) counts 0.5. Public holidays are not known yet, so they count
 * as working days (HR can adjust a balance when that matters).
 */
public final class LeaveDays {

    static final BigDecimal HALF = new BigDecimal("0.5");

    private LeaveDays() {
    }

    public static BigDecimal count(LocalDate start, LocalDate end, boolean halfStart, boolean halfEnd) {
        BigDecimal total = BigDecimal.ZERO.setScale(1);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!working(d)) {
                continue;
            }
            boolean half = (halfStart && d.equals(start)) || (halfEnd && d.equals(end));
            total = total.add(half ? HALF : BigDecimal.ONE);
        }
        return total;
    }

    public static boolean working(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
