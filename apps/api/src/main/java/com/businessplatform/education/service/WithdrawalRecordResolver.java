package com.businessplatform.education.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.education.domain.Withdrawal;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.repository.WithdrawalRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/**
 * Lets the approval engine check withdrawal requests: admins and the teachers who see the student may see one;
 * only its requester asks for approval, while it is waiting. Nothing while the module is off or for members
 * without an Education role.
 */
@Component
@RequiredArgsConstructor
public class WithdrawalRecordResolver implements RecordResolver {

    private final EducationAccess access;
    private final WithdrawalRepository withdrawals;
    private final StudentRepository students;

    @Override
    public String module() {
        return EducationAccess.MODULE;
    }

    @Override
    public boolean handles(String recordType) {
        return WithdrawalService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        return access.usable(ctx).flatMap(edu -> withdrawals.findByIdAndOrgId(recordId, ctx.orgId())
                .filter(w -> edu.admin() || w.getRequestedBy().equals(ctx.userId()) || access.sees(edu, w.getStudentId()))
                .map(w -> new RecordView(WithdrawalService.title(students, w), WithdrawalService.link(ctx.slug(), w.getStudentId()),
                        w.getRequestedBy().equals(ctx.userId()) && Withdrawal.PENDING.equals(w.getStatus()))));
    }

    @Override
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return Optional.empty();
    }
}
