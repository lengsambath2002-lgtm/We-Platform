package com.businessplatform.hr.service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Working days in a leave request: Monday to Friday count; weekends and the organisation's public
 * holidays don't. A half first day (starts at midday) or half last day (ends at midday) counts 0.5.
 */
public final class LeaveDays {

    static final BigDecimal HALF = new BigDecimal("0.5");

    private LeaveDays() {
    }

    public static BigDecimal count(LocalDate start, LocalDate end, boolean halfStart, boolean halfEnd,
            Set<LocalDate> holidays) {
        BigDecimal total = BigDecimal.ZERO.setScale(1);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!working(d) || holidays.contains(d)) {
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
