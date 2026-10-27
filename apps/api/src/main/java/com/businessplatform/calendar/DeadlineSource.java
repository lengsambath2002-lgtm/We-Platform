package com.businessplatform.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.org.service.OrgContext;

/** A module's dated items shown on the calendar (task due dates …), already filtered to what the person may see. */
public interface DeadlineSource {

    List<Deadline> deadlines(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly);

    /** {@code projectId}: lets the calendar check visibility of project-linked events through the same module. */
    boolean canSeeProject(OrgContext ctx, UUID projectId);

    record Deadline(String module, UUID id, String key, String title, LocalDate date, String status, String link,
            boolean mine) {
    }
}
