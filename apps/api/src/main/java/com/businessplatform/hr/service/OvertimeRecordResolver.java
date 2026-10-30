package com.businessplatform.hr.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.hr.domain.OvertimeRequest;
import com.businessplatform.hr.repository.OvertimeRequestRepository;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/**
 * Lets the approval engine check overtime requests: the person, their manager and HR managers see one; only
 * the person asks for approval, and MANAGER steps go to their manager. Nothing while HR is switched off.
 */
@Component
@RequiredArgsConstructor
public class OvertimeRecordResolver implements RecordResolver {

    private final HrAccess hr;
    private final OvertimeRequestRepository requests;
    private final UserRepository users;
    private final ProfileService profiles;

    @Override
    public String module() {
        return HrAccess.MODULE;
    }

    @Override
    public boolean handles(String recordType) {
        return OvertimeService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        if (!hr.usable(ctx)) {
            return Optional.empty();
        }
        return requests.findByIdAndOrgId(recordId, ctx.orgId())
                .filter(o -> o.getUserId().equals(ctx.userId()) || HrAccess.manages(ctx)
                        || profiles.managerOf(ctx.orgId(), o.getUserId()).map(ctx.userId()::equals).orElse(false)
                        || profiles.secondManagerOf(ctx.orgId(), o.getUserId()).map(ctx.userId()::equals).orElse(false))
                .map(o -> new RecordView(OvertimeService.title(o, users.findById(o.getUserId()).orElseThrow()),
                        OvertimeService.link(ctx),
                        o.getUserId().equals(ctx.userId()) && OvertimeRequest.PENDING.equals(o.getStatus())));
    }

    @Override
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> managerOf(OrgContext ctx, String recordType, UUID recordId) {
        return requests.findByIdAndOrgId(recordId, ctx.orgId()).flatMap(o -> profiles.managerOf(ctx.orgId(), o.getUserId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> secondManagerOf(OrgContext ctx, String recordType, UUID recordId) {
        return requests.findByIdAndOrgId(recordId, ctx.orgId()).flatMap(o -> profiles.secondManagerOf(ctx.orgId(), o.getUserId()));
    }
}
