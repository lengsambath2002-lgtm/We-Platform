package com.businessplatform.ops.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * A ticket's SLA, derived (never stored). Each target is MET or BREACHED once it happened (responded / resolved),
 * and ON_TRACK, AT_RISK (past 75% of its window) or BREACHED while it hasn't. The ticket's state is the worst of
 * the two: BREACHED, then AT_RISK, then ON_TRACK; MET only when both were met.
 */
public final class Sla {

    public static final String ON_TRACK = "ON_TRACK";
    public static final String AT_RISK = "AT_RISK";
    public static final String BREACHED = "BREACHED";
    public static final String MET = "MET";

    /** The share of a window after which a target is at risk. */
    public static final double WARN_SHARE = 0.75;

    private Sla() {
    }

    /** Sets the due and warn times from {@code from} (the ticket's creation). */
    public static void apply(Ticket t, SlaPolicy p, Instant from) {
        t.setResponseDueAt(from.plus(Duration.ofMinutes(p.getResponseMinutes())));
        t.setResponseWarnAt(from.plus(Duration.ofSeconds(Math.round(p.getResponseMinutes() * 60 * WARN_SHARE))));
        t.setResolutionDueAt(from.plus(Duration.ofMinutes(p.getResolutionMinutes())));
        t.setResolutionWarnAt(from.plus(Duration.ofSeconds(Math.round(p.getResolutionMinutes() * 60 * WARN_SHARE))));
    }

    public static String target(Instant done, Instant warn, Instant due, Instant now) {
        if (done != null) {
            return done.isAfter(due) ? BREACHED : MET;
        }
        return now.isAfter(due) ? BREACHED : !now.isBefore(warn) ? AT_RISK : ON_TRACK;
    }

    public static String response(Ticket t, Instant now) {
        return target(t.getRespondedAt(), t.getResponseWarnAt(), t.getResponseDueAt(), now);
    }

    public static String resolution(Ticket t, Instant now) {
        return target(t.getResolvedAt(), t.getResolutionWarnAt(), t.getResolutionDueAt(), now);
    }

    public static String overall(Ticket t, Instant now) {
        String a = response(t, now);
        String b = resolution(t, now);
        if (BREACHED.equals(a) || BREACHED.equals(b)) {
            return BREACHED;
        }
        if (AT_RISK.equals(a) || AT_RISK.equals(b)) {
            return AT_RISK;
        }
        return MET.equals(a) && MET.equals(b) ? MET : ON_TRACK;
    }
}
