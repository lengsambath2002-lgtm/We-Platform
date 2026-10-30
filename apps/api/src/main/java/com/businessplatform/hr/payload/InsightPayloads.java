package com.businessplatform.hr.payload;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

/** HR & Workforce: the HR dashboard and the org chart. */
public final class InsightPayloads {

    private InsightPayloads() {
    }

    public record Count(String label, long count) {
    }

    /** Today's attendance by status (PRESENT, LATE, ABSENT, LEAVE, NOT_IN, HOLIDAY, DAY_OFF). */
    public record Today(long present, long late, long absent, long onLeave, long notIn, long offToday) {
    }

    public record Hiring(long openJobs, long activeCandidates, long onboarding) {
    }

    public record PayrollSnapshot(UUID runId, String month, String status, BigDecimal net, BigDecimal employerCost, int people) {
    }

    public record ReviewSnapshot(UUID cycleId, String name, int total, int reviewed) {
    }

    /** {@code payroll}: the latest pay run, if any; {@code reviews}: open cycles. */
    public record Dashboard(long headcount, long joinedLast90Days, List<Count> departments, Today today,
            long pendingLeave, long pendingOvertime, Hiring hiring, PayrollSnapshot payroll, List<ReviewSnapshot> reviews,
            long trainingCompletedThisYear) {
    }

    /** One person in the org chart; {@code managerId} null = at the top. */
    public record ChartNode(PersonRef person, String title, String department, UUID managerId) {
    }
}
