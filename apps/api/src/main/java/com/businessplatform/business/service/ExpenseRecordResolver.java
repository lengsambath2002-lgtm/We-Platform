package com.businessplatform.business.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.business.domain.Expense;
import com.businessplatform.business.repository.ExpenseRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.service.RecordResolver;

import lombok.RequiredArgsConstructor;

/**
 * Lets the approval engine check expenses. Any non-guest member may see that an expense exists (the engine
 * itself shows a request only to the people involved: its requester, its approvers and Admins); only the
 * person who spent it asks for approval, while it is waiting. Nothing while the module is switched off.
 */
@Component
@RequiredArgsConstructor
public class ExpenseRecordResolver implements RecordResolver {

    private final BusinessAccess access;
    private final ExpenseRepository expenses;
    private final ExpenseTitles titles;

    @Override
    public String module() {
        return BusinessAccess.MODULE;
    }

    @Override
    public boolean handles(String recordType) {
        return ExpenseService.RECORD.equals(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordView> view(OrgContext ctx, String recordType, UUID recordId) {
        if (!access.usable(ctx)) {
            return Optional.empty();
        }
        return expenses.findByIdAndOrgId(recordId, ctx.orgId()).map(e -> new RecordView(titles.title(e),
                ExpenseService.link(ctx, e.getId()), e.getUserId().equals(ctx.userId()) && Expense.PENDING.equals(e.getStatus())));
    }

    @Override
    public Optional<UUID> leadOf(OrgContext ctx, String recordType, UUID recordId) {
        return Optional.empty();
    }
}
