package com.businessplatform.activity;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.businessplatform.org.service.OrgContext;

/**
 * Implemented by each module for its record types, so the audit log can name the records and link to
 * them without the core knowing about projects, events or students. A record that is gone has no label
 * (the log then falls back to what the entry itself says).
 */
public interface RecordLabeler {

    boolean handles(String recordType);

    /** Labels for the records of {@code recordType} among {@code ids} that still exist in the organisation. */
    Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids);

    record Label(String title, String link) {
    }
}
