package com.businessplatform.org.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place that turns "organisation slug + signed-in user" into an {@link OrgContext}.
 * Someone without an active membership gets NOT_FOUND (an organisation's existence isn't revealed);
 * so does everyone for a deleted organisation.
 * Every org-scoped service starts here; queries then always filter by {@code ctx.orgId()}.
 */
@Component
@RequiredArgsConstructor
public class OrgAccess {

    private final OrganisationRepository orgs;
    private final MembershipRepository memberships;

    @Transactional(readOnly = true)
    public OrgContext require(String slug) {
        var userId = CurrentUser.id();
        Organisation o = orgs.findBySlug(slug).filter(x -> !x.isDeleted()).orElseThrow(BusinessException::notFound);
        Membership m = memberships.findByOrgIdAndUserId(o.getId(), userId)
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()))
                .orElseThrow(BusinessException::notFound);
        return new OrgContext(o.getId(), o.getSlug(), o.getName(), userId, m.getId(), m.getRole());
    }

    /** {@link #require} plus organisation Owner or Admin. */
    public OrgContext requireAdmin(String slug) {
        OrgContext ctx = require(slug);
        if (!ctx.isAdmin()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }
}
