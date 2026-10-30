package com.businessplatform.education.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Results and rates, computed only here (the API never trusts a client's numbers). Percentages keep one decimal,
 * rounded half-up at the end of each step:
 * <ul>
 * <li>a course result for a term: {@code Σ(score / max × weight) / Σ weight × 100} over the assessments that are
 * graded (an assessment without a score doesn't count yet); none graded = no result;</li>
 * <li>a term average: the mean of the student's course results;</li>
 * <li>the letter (Cambodian scale): A ≥ 90, B ≥ 80, C ≥ 70, D ≥ 60, E ≥ 50, F below;</li>
 * <li>a rank in the class: by average, highest first; equal averages share a rank (1, 2, 2, 4);</li>
 * <li>an attendance rate: {@code (present + late) / (present + late + absent) × 100}; excused days don't count.</li>
 * </ul>
 */
public final class Grading {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = new BigDecimal(100);

    private Grading() {
    }

    /** One assessment for a student: {@code score} null when not graded. */
    public record Mark(BigDecimal score, BigDecimal max, BigDecimal weight) {
    }

    public static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(1, RoundingMode.HALF_UP);
    }

    /** The course result in percent (one decimal), or null when nothing is graded. */
    public static BigDecimal courseResult(Collection<Mark> marks) {
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal weights = BigDecimal.ZERO;
        for (Mark m : marks) {
            if (m.score() == null) {
                continue;
            }
            earned = earned.add(m.score().multiply(m.weight()).divide(m.max(), MC));
            weights = weights.add(m.weight());
        }
        if (weights.signum() == 0) {
            return null;
        }
        return round(earned.multiply(HUNDRED).divide(weights, MC));
    }

    /** The mean of the results that exist (one decimal), or null when there are none. */
    public static BigDecimal average(Collection<BigDecimal> results) {
        List<BigDecimal> some = results.stream().filter(Objects::nonNull).toList();
        if (some.isEmpty()) {
            return null;
        }
        BigDecimal sum = some.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return round(sum.divide(new BigDecimal(some.size()), MC));
    }

    public static String letter(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        double p = percent.doubleValue();
        return p >= 90 ? "A" : p >= 80 ? "B" : p >= 70 ? "C" : p >= 60 ? "D" : p >= 50 ? "E" : "F";
    }

    /** Ranks by value, highest first; equal values share a rank and the next rank skips (1, 2, 2, 4). */
    public static <K> Map<K, Integer> ranks(Map<K, BigDecimal> values) {
        List<Map.Entry<K, BigDecimal>> sorted = new ArrayList<>(values.entrySet().stream().filter(e -> e.getValue() != null).toList());
        sorted.sort(Map.Entry.<K, BigDecimal>comparingByValue(Comparator.reverseOrder()));
        Map<K, Integer> out = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            int rank = i > 0 && sorted.get(i).getValue().compareTo(sorted.get(i - 1).getValue()) == 0
                    ? out.get(sorted.get(i - 1).getKey()) : i + 1;
            out.put(sorted.get(i).getKey(), rank);
        }
        return out;
    }

    /** The attendance rate in percent (one decimal), or null when no day counts. */
    public static BigDecimal attendanceRate(long present, long late, long absent) {
        long days = present + late + absent;
        if (days == 0) {
            return null;
        }
        return round(new BigDecimal(present + late).multiply(HUNDRED).divide(new BigDecimal(days), MC));
    }

    /** Cents, half-up. */
    public static BigDecimal cents(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
