package com.businessplatform.hr.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.hr.domain.LeaveRequest;
import com.businessplatform.hr.repository.LeaveRequestRepository;
import com.businessplatform.hr.repository.LeaveTypeRepository;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/**
 * Lets the approval engine check leave requests. Any non-guest member may see that a colleague asked
 * for leave (the team's absences are shared anyway; the reason only shows to the people involved); only
 * the requester asks for approval, and MANAGER steps go to the requester's manager. Nothing while HR is
 * switched off.
 */
@Component
@RequiredArgsConstructor
public class LeaveRecordResolver implements RecordResolver {

    private final HrAccess hr;
    private final LeaveRequestRepository leaves;
    private final LeaveTypeRepository types;
    private final UserRepository users;
    private final ProfileService profiles;

    @Override
    public String module() {
        return HrAccess.MODULE;
    }

    @Override
    public boolean handles(String recordType) {
        return LeaveService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        if (!hr.usable(ctx)) {
            return Optional.empty();
        }
        return leaves.findByIdAndOrgId(recordId, ctx.orgId()).map(l -> new RecordView(
                LeaveService.title(l, types.findById(l.getLeaveTypeId()).orElseThrow(), users.findById(l.getUserId()).orElseThrow()),
                LeaveService.link(ctx, l),
                l.getUserId().equals(ctx.userId()) && LeaveRequest.PENDING.equals(l.getStatus())));
    }

    @Override
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> managerOf(OrgContext ctx, String recordType, UUID recordId) {
        return leaves.findByIdAndOrgId(recordId, ctx.orgId()).flatMap(l -> profiles.managerOf(ctx.orgId(), l.getUserId()));
    }
}
