package com.businessplatform.hr.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.hr.domain.LeaveType;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.hr.repository.LeaveTypeRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of leave requests, profiles and leave types for the audit log. */
@Component
@RequiredArgsConstructor
public class HrRecordLabeler implements RecordLabeler {

    private final LeaveRequestRepository leaves;
    private final LeaveTypeRepository types;
    private final EmployeeProfileRepository profiles;
    private final UserRepository users;

    @Override
    public boolean handles(String recordType) {
        return LeaveService.RECORD.equals(recordType) || ProfileService.RECORD.equals(recordType)
                || LeaveTypeService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/hr";
        switch (recordType) {
            case LeaveService.RECORD -> {
                var found = leaves.findAllById(ids).stream().filter(l -> l.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(l -> l.getUserId()).toList());
                Map<UUID, LeaveType> byId = new HashMap<>();
                types.findAllById(found.stream().map(l -> l.getLeaveTypeId()).distinct().toList()).forEach(t -> byId.put(t.getId(), t));
                found.forEach(l -> out.put(l.getId(), new Label(
                        LeaveService.title(l, byId.get(l.getLeaveTypeId()), people.get(l.getUserId())), LeaveService.link(ctx, l))));
            }
            case ProfileService.RECORD -> {
                var found = profiles.findAllById(ids).stream().filter(p -> p.getOrgId().equals(ctx.orgId())).toList();
                Map<UUID, AppUser> people = people(found.stream().map(p -> p.getUserId()).toList());
                found.forEach(p -> out.put(p.getId(), new Label(people.get(p.getUserId()).getName(),
                        base + "/people/" + p.getUserId())));
            }
            default -> types.findAllById(ids).stream().filter(t -> t.getOrgId().equals(ctx.orgId()))
                    .forEach(t -> out.put(t.getId(), new Label(t.getNameEn(), base + "/settings")));
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
