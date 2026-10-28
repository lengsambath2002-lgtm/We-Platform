package com.businessplatform.workflow.service;

import java.util.UUID;

import com.businessplatform.org.service.OrgContext;

/**
 * A request reached its end: {@code status} is APPROVED, REJECTED or WITHDRAWN. Published synchronously
 * inside the deciding transaction; a module listens for its own {@code module}/{@code recordType}.
 * {@code ctx} is the person who closed it.
 */
public record ApprovalClosed(OrgContext ctx, UUID requestId, String module, String recordType, UUID recordId,
        String status) {
}
