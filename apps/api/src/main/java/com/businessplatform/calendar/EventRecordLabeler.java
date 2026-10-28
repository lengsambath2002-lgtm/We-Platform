package com.businessplatform.calendar;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of calendar events for the audit log. */
@Component
@RequiredArgsConstructor
public class EventRecordLabeler implements RecordLabeler {

    private final CalendarEventRepository events;

    @Override
    public boolean handles(String recordType) {
        return CalendarService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        events.findAllById(ids).stream().filter(e -> e.getOrgId().equals(ctx.orgId()))
                .forEach(e -> out.put(e.getId(), new Label(e.getTitle(), CalendarService.link(ctx, e))));
        return out;
    }
}
