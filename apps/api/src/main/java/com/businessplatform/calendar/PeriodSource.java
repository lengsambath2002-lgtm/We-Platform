package com.businessplatform.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.org.service.OrgContext;

/**
 * A module's whole-day spans shown on the calendar (approved leave …), already filtered to what the
 * person may see. Unlike a {@link DeadlineSource.Deadline} (one day, something due), a period covers
 * {@code start}–{@code end} inclusive; {@code kind} tells the client how to word and show it.
 */
public interface PeriodSource {

    List<Period> periods(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly);

    /** {@code halfStart}/{@code halfEnd}: the first day starts, or the last day ends, at midday. */
    record Period(String module, String kind, UUID id, String title, LocalDate start, LocalDate end, boolean halfStart,
            boolean halfEnd, String link, boolean mine) {
    }
}
