package com.businessplatform.hr.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.hr.repository.AttendanceRecordRepository;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.hr.repository.WorkScheduleRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of attendance records, overtime requests and work schedules for the audit log. */
@Component
@RequiredArgsConstructor
public class AttendanceRecordLabeler implements RecordLabeler {

    private final AttendanceRecordRepository records;
    private final OvertimeRequestRepository overtime;
    private final WorkScheduleRepository schedules;
    private final UserRepository users;

    @Override
    public boolean handles(String recordType) {
        return TimekeepingService.RECORD.equals(recordType) || OvertimeService.RECORD.equals(recordType)
                || ScheduleService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/hr";
        switch (recordType) {
            case TimekeepingService.RECORD -> {
                var found = records.findAllById(ids).stream().filter(r -> r.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(r -> r.getUserId()).toList());
                found.forEach(r -> out.put(r.getId(), new Label(people.get(r.getUserId()).getName() + " · " + r.getWorkDate(),
                        base + "/attendance/" + r.getUserId() + "?month=" + r.getWorkDate().toString().substring(0, 7))));
            }
            case OvertimeService.RECORD -> {
                var found = overtime.findAllById(ids).stream().filter(o -> o.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(o -> o.getUserId()).toList());
                found.forEach(o -> out.put(o.getId(), new Label(OvertimeService.title(o, people.get(o.getUserId())),
                        OvertimeService.link(ctx))));
            }
            default -> schedules.findAllById(ids).stream().filter(s -> s.getOrgId().equals(ctx.orgId()))
                    .forEach(s -> out.put(s.getId(), new Label(s.getName(), base + "/settings")));
        }
        return out;
    }

    private Map<UUID, AppUser> people(Collection<UUID> ids) {
        Map<UUID, AppUser> out = new HashMap<>();
        if (!ids.isEmpty()) {
            users.findByIdIn(ids.stream().distinct().toList()).forEach(u -> out.put(u.getId(), u));
        }
        return out;
    }
}
