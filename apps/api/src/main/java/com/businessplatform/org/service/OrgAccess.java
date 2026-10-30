package com.businessplatform.org.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgAssignment;
import com.businessplatform.org.domain.OrgCustomRole;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.OrgRoleGrant;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;
import com.businessplatform.org.repository.RoleRepositories.OrgCustomRoleRepository;
import com.businessplatform.org.repository.RoleRepositories.OrgRoleGrantRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgAssignmentRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgPositionRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgUnitRepository;

import lombok.RequiredArgsConstructor;

/**
 * The single place that turns "organisation slug + signed-in user" into an {@link OrgContext}.
 * Someone without an active membership gets NOT_FOUND (an organisation's existence isn't revealed);
 * so does everyone for a deleted organisation. A suspended one answers ORG_SUSPENDED to its members.
 * Every org-scoped service starts here; queries then always filter by {@code ctx.orgId()}.
 */
@Component
@RequiredArgsConstructor
public class OrgAccess {

    private final OrganisationRepository orgs;
    private final MembershipRepository memberships;
    private final OrgRoleGrantRepository grants;
    private final OrgCustomRoleRepository roles;
    private final OrgUnitRepository units;
    private final OrgPositionRepository positions;
    private final OrgAssignmentRepository assignments;

    @Transactional(readOnly = true)
    public OrgContext require(String slug) {
        var userId = CurrentUser.id();
        Organisation o = orgs.findBySlug(slug).filter(x -> !x.isDeleted()).orElseThrow(BusinessException::notFound);
        Membership m = memberships.findByOrgIdAndUserId(o.getId(), userId)
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()))
                .orElseThrow(BusinessException::notFound);
        if (o.isSuspended()) {
            throw new BusinessException(ErrorCode.ORG_SUSPENDED);
        }
        Grants grants = m.getRole() == OrgRole.MEMBER ? grantsOf(o.getId(), m) : Grants.NONE;
        return new OrgContext(o.getId(), o.getSlug(), o.getName(), userId, m.getId(), m.getRole(), grants);
    }

    /** The member's custom roles, resolved: scoped permissions cover active members placed in the unit or below. */
    private Grants grantsOf(UUID orgId, Membership m) {
        List<OrgRoleGrant> held = grants.findByMembershipId(m.getId());
        if (held.isEmpty()) {
            return Grants.NONE;
        }
        Map<UUID, OrgCustomRole> byId = roles.findAllById(held.stream().map(OrgRoleGrant::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(OrgCustomRole::getId, r -> r));
        Set<String> orgWide = new HashSet<>();
        Map<String, Set<UUID>> scopedUnits = new HashMap<>();
        for (OrgRoleGrant g : held) {
            OrgCustomRole r = byId.get(g.getRoleId());
            if (r == null) {
                continue;
            }
            for (String p : r.getPermissions()) {
                boolean scoped = RoleCatalogue.find(p).map(RoleCatalogue.Entry::scoped).orElse(false);
                if (g.getUnitId() == null || !scoped) {
                    orgWide.add(p);
                } else {
                    scopedUnits.computeIfAbsent(p, k -> new HashSet<>()).add(g.getUnitId());
                }
            }
        }
        Map<String, Set<UUID>> scoped = new HashMap<>();
        if (!scopedUnits.isEmpty()) {
            Map<UUID, UUID> parent = new HashMap<>();
            units.findByOrgId(orgId).stream().filter(u -> !u.isArchived()).forEach(u -> parent.put(u.getId(), u.getParentId()));
            Map<UUID, UUID> positionUnit = new HashMap<>();
            positions.findByOrgId(orgId).forEach(p -> positionUnit.put(p.getId(), p.getUnitId()));
            Map<UUID, UUID> userOf = memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                    .filter(x -> Membership.ACTIVE.equals(x.getStatus()) && x.getRole() != OrgRole.GUEST)
                    .collect(Collectors.toMap(Membership::getId, Membership::getUserId));
            List<OrgAssignment> placed = assignments.findByOrgId(orgId);
            scopedUnits.forEach((perm, roots) -> {
                if (orgWide.contains(perm)) {
                    return;
                }
                Set<UUID> people = new HashSet<>();
                for (OrgAssignment a : placed) {
                    UUID unit = a.getUnitId() != null ? a.getUnitId() : positionUnit.get(a.getPositionId());
                    UUID user = userOf.get(a.getMembershipId());
                    if (unit != null && user != null && !user.equals(m.getUserId()) && within(parent, unit, roots)) {
                        people.add(user);
                    }
                }
                scoped.put(perm, people);
            });
        }
        return new Grants(orgWide, scoped, held.stream().map(g -> byId.get(g.getRoleId())).filter(java.util.Objects::nonNull)
                .map(OrgCustomRole::getName).distinct().sorted().toList());
    }

    private static boolean within(Map<UUID, UUID> parent, UUID unit, Set<UUID> roots) {
        Set<UUID> seen = new HashSet<>();
        for (UUID at = unit; at != null && seen.add(at); at = parent.get(at)) {
            if (roots.contains(at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Another active member's context in the same organisation (an acting approver sees a record as the person they
     * act for). Empty for anyone who isn't an active member.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<OrgContext> asMember(OrgContext ctx, UUID userId) {
        return memberships.findByOrgIdAndUserId(ctx.orgId(), userId).filter(x -> Membership.ACTIVE.equals(x.getStatus()))
                .map(m -> new OrgContext(ctx.orgId(), ctx.slug(), ctx.orgName(), userId, m.getId(), m.getRole(),
                        m.getRole() == OrgRole.MEMBER ? grantsOf(ctx.orgId(), m) : Grants.NONE));
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
