package com.businessplatform.workflow.service;

import java.util.Optional;
import java.util.UUID;

import com.businessplatform.org.service.OrgContext;

/**
 * Implemented by each module for its record types, so the approval engine can check, on the server,
 * that a person may see a record before showing or deciding an approval about it — without the engine
 * knowing anything about projects, students or products.
 */
public interface RecordResolver {

    /** The module this resolver answers for (e.g. {@code projects}). */
    String module();

    /** The record types it answers for (e.g. {@code task}). */
    boolean handles(String recordType);

    /** The record as {@code ctx}'s person may see it, or empty when it doesn't exist or they may not see it. */
    Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId);

    /** Who leads the record (for PROJECT_LEAD approval steps), if anyone. */
    Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId);

    record RecordView(String title, String link, boolean canRequestApproval) {
    }
}
