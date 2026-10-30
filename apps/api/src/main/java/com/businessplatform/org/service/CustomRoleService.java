package com.businessplatform.org.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgCustomRole;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.OrgRoleGrant;
import com.businessplatform.org.domain.OrgUnit;
import com.businessplatform.org.payload.RolePayloads.*;
import com.businessplatform.org.payload.StructurePayloads.PersonBrief;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.RoleRepositories.OrgCustomRoleRepository;
import com.businessplatform.org.repository.RoleRepositories.OrgRoleGrantRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgUnitRepository;

import lombok.RequiredArgsConstructor;

/**
 * Custom roles: Owners and Admins name a set of permissions from {@link RoleCatalogue} and grant it to members, for
 * the whole organisation or one unit and below. Only Owners and Admins manage roles, so no role can hand out more
 * than they have. Guests can't hold roles; Owners and Admins don't need them.
 */
@Service
@RequiredArgsConstructor
public class CustomRoleService {

    private final OrgAccess access;
    private final OrgCustomRoleRepository roles;
    private final OrgRoleGrantRepository grants;
    private final OrgUnitRepository units;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ModuleService modules;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RolesView view(String slug) {
        OrgContext ctx = access.requireAdmin(slug);
        Set<String> on = modules.enabled(ctx.orgId());
        List<CatalogueEntry> catalogue = RoleCatalogue.ENTRIES.stream()
                .map(e -> new CatalogueEntry(e.code(), e.module(), e.scoped(), e.module() == null || on.contains(e.module()))).toList();
        List<OrgRoleGrant> gs = grants.findByOrgId(ctx.orgId());
        Map<UUID, Membership> members = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .collect(Collectors.toMap(Membership::getId, Function.identity()));
        Map<UUID, AppUser> people = users.findByIdIn(members.values().stream().map(Membership::getUserId).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        Map<UUID, OrgUnit> us = units.findByOrgId(ctx.orgId()).stream().collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        List<RoleView> roleViews = roles.findByOrgIdOrderByName(ctx.orgId()).stream()
                .map(r -> view(r, (int) gs.stream().filter(g -> g.getRoleId().equals(r.getId())).count())).toList();
        List<GrantView> grantViews = gs.stream().filter(g -> members.containsKey(g.getMembershipId())).map(g -> {
            Membership m = members.get(g.getMembershipId());
            AppUser u = people.get(m.getUserId());
            OrgUnit unit = g.getUnitId() == null ? null : us.get(g.getUnitId());
            return new GrantView(g.getId(), g.getRoleId(), new PersonBrief(u.getId(), m.getId(), u.getName(), u.getAvatarUrl()),
                    g.getUnitId(), unit == null ? null : unit.getName(), g.getCreatedAt());
        }).sorted(Comparator.comparing((GrantView g) -> g.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        return new RolesView(catalogue, roleViews, grantViews);
    }

    @Transactional
    public RoleView save(String slug, UUID id, RoleRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        OrgCustomRole r = id == null ? new OrgCustomRole() : role(ctx, id);
        UUID self = id == null ? Ids.next() : id;
        String name = in.name().strip();
        if (roles.nameTaken(ctx.orgId(), name, self)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "There is already a role with this name");
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (String p : in.permissions()) {
            if (RoleCatalogue.find(p).isEmpty()) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "permissions", "Unknown permission");
            }
            wanted.add(p);
        }
        if (wanted.isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "permissions", "Choose at least one permission");
        }
        Instant now = clock.instant();
        if (id == null) {
            r.setId(self);
            r.setOrgId(ctx.orgId());
            r.setCreatedAt(now);
        }
        r.setName(name);
        r.setDescription(in.description() == null || in.description().isBlank() ? null : in.description().strip());
        r.setPermissions(RoleCatalogue.ENTRIES.stream().map(RoleCatalogue.Entry::code).filter(wanted::contains).toList());
        r.setUpdatedAt(now);
        roles.save(r);
        audit(ctx, "role", r.getId(), id == null ? "org.role.created" : "org.role.updated", Map.of("name", name));
        return view(r, (int) grants.findByOrgId(ctx.orgId()).stream().filter(g -> g.getRoleId().equals(r.getId())).count());
    }

    /** Deletes the role; everyone holding it loses it. */
    @Transactional
    public void delete(String slug, UUID id) {
        OrgContext ctx = access.requireAdmin(slug);
        OrgCustomRole r = role(ctx, id);
        roles.delete(r);
        audit(ctx, "role", r.getId(), "org.role.deleted", Map.of("name", r.getName()));
    }

    @Transactional
    public GrantView grant(String slug, UUID roleId, GrantRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        OrgCustomRole r = role(ctx, roleId);
        Membership m = memberships.findById(in.membershipId()).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()))
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "membershipId", "Choose a member of the organisation"));
        if (m.getRole() != OrgRole.MEMBER) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "membershipId",
                    "Roles are for members: Owners and Admins can already do everything, guests can't hold roles");
        }
        OrgUnit unit = null;
        if (in.unitId() != null) {
            unit = units.findByIdAndOrgId(in.unitId(), ctx.orgId()).filter(u -> !u.isArchived())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "unitId", "Choose an active unit"));
            if (r.getPermissions().stream().noneMatch(p -> RoleCatalogue.find(p).map(RoleCatalogue.Entry::scoped).orElse(false))) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "unitId", "This role always applies to the whole organisation");
            }
        }
        if (grants.exists(r.getId(), m.getId(), in.unitId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "They already have this role here", List.of());
        }
        OrgRoleGrant g = new OrgRoleGrant();
        g.setId(Ids.next());
        g.setOrgId(ctx.orgId());
        g.setRoleId(r.getId());
        g.setMembershipId(m.getId());
        g.setUnitId(in.unitId());
        g.setGrantedBy(ctx.userId());
        g.setCreatedAt(clock.instant());
        grants.save(g);
        AppUser u = users.findById(m.getUserId()).orElseThrow();
        audit(ctx, "member", u.getId(), "org.role.granted", unit == null ? Map.of("name", u.getName(), "role", r.getName())
                : Map.of("name", u.getName(), "role", r.getName(), "unit", unit.getName()));
        return new GrantView(g.getId(), r.getId(), new PersonBrief(u.getId(), m.getId(), u.getName(), u.getAvatarUrl()),
                g.getUnitId(), unit == null ? null : unit.getName(), g.getCreatedAt());
    }

    @Transactional
    public void revoke(String slug, UUID grantId) {
        OrgContext ctx = access.requireAdmin(slug);
        OrgRoleGrant g = grants.findByIdAndOrgId(grantId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        OrgCustomRole r = roles.findById(g.getRoleId()).orElseThrow();
        Membership m = memberships.findById(g.getMembershipId()).orElseThrow();
        String name = users.findById(m.getUserId()).map(AppUser::getName).orElse("");
        grants.delete(g);
        audit(ctx, "member", m.getUserId(), "org.role.revoked", Map.of("name", name, "role", r.getName()));
    }

    /** Each member's custom role names (membership id → names), for the members list. */
    @Transactional(readOnly = true)
    public Map<UUID, List<String>> roleNames(UUID orgId) {
        Map<UUID, String> names = roles.findByOrgIdOrderByName(orgId).stream()
                .collect(Collectors.toMap(OrgCustomRole::getId, OrgCustomRole::getName));
        return grants.findByOrgId(orgId).stream().collect(Collectors.groupingBy(OrgRoleGrant::getMembershipId,
                Collectors.mapping(g -> names.get(g.getRoleId()), Collectors.collectingAndThen(Collectors.toList(),
                        l -> l.stream().filter(Objects::nonNull).distinct().sorted().toList()))));
    }

    private OrgCustomRole role(OrgContext ctx, UUID id) {
        return roles.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private static RoleView view(OrgCustomRole r, int holders) {
        boolean scopable = r.getPermissions().stream().anyMatch(p -> RoleCatalogue.find(p).map(RoleCatalogue.Entry::scoped).orElse(false));
        return new RoleView(r.getId(), r.getName(), r.getDescription(), r.getPermissions(), scopable, holders);
    }

    private void audit(OrgContext ctx, String recordType, UUID recordId, String verb, Map<String, Object> data) {
        activity.record(ctx.orgId(), "org", recordType, recordId, ctx.userId(), verb, data);
    }
}
