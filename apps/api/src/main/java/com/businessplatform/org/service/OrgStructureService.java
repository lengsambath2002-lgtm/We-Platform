package com.businessplatform.org.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgAssignment;
import com.businessplatform.org.domain.OrgPosition;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.OrgUnit;
import com.businessplatform.org.payload.StructurePayloads.*;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.RoleRepositories.OrgRoleGrantRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgAssignmentRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgPositionRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgUnitRepository;

import lombok.RequiredArgsConstructor;

/**
 * The organisation structure: a tree of units (branch, division, department, center, section, team), positions
 * with a planned headcount, and members' places in it (one primary, any number of additional). Every member but
 * guests reads it; Owners and Admins change it. A member's primary position is their job title, and HR's
 * department follows their primary unit (see {@link StructureChanged}).
 */
@Service
@RequiredArgsConstructor
public class OrgStructureService {

    private final OrgAccess access;
    private final OrgUnitRepository units;
    private final OrgPositionRepository positions;
    private final OrgAssignmentRepository assignments;
    private final OrgRoleGrantRepository roleGrants;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StructureView view(String slug) {
        OrgContext ctx = reader(slug);
        List<OrgUnit> us = units.findByOrgId(ctx.orgId());
        List<OrgPosition> ps = positions.findByOrgId(ctx.orgId());
        Map<UUID, Membership> active = active(ctx.orgId());
        Map<UUID, Membership> byMembership = active.values().stream().collect(Collectors.toMap(Membership::getId, Function.identity()));
        List<OrgAssignment> as = assignments.findByOrgId(ctx.orgId()).stream()
                .filter(a -> byMembership.containsKey(a.getMembershipId())).toList();
        Map<UUID, AppUser> people = people(active.keySet());

        Map<UUID, Set<UUID>> inUnit = new HashMap<>();
        Map<UUID, Long> holders = new HashMap<>();
        Map<UUID, OrgPosition> posById = ps.stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        for (OrgAssignment a : as) {
            UUID unit = unitOf(a, posById);
            if (unit != null) {
                inUnit.computeIfAbsent(unit, k -> new HashSet<>()).add(a.getMembershipId());
            }
            if (a.getPositionId() != null) {
                holders.merge(a.getPositionId(), 1L, Long::sum);
            }
        }
        List<UnitView> unitViews = us.stream()
                .map(u -> new UnitView(u.getId(), u.getParentId(), u.getKind(), u.getName(), u.getCode(),
                        brief(active.get(u.getHeadUserId()), people), u.getAddress(), u.getPhone(), u.isArchived(),
                        inUnit.getOrDefault(u.getId(), Set.of()).size()))
                .sorted(Comparator.comparing(UnitView::name, String.CASE_INSENSITIVE_ORDER)).toList();
        List<PositionView> positionViews = ps.stream()
                .map(p -> new PositionView(p.getId(), p.getUnitId(), p.getTitle(), p.getCode(), p.getGrade(),
                        p.getReportsToId(), p.getHeadcount(), holders.getOrDefault(p.getId(), 0L).intValue(),
                        p.getDescription(), p.isArchived()))
                .sorted(Comparator.comparing(PositionView::title, String.CASE_INSENSITIVE_ORDER)).toList();
        List<AssignmentView> assignmentViews = as.stream().map(a -> {
            Membership m = byMembership.get(a.getMembershipId());
            return new AssignmentView(a.getId(), brief(m, people), m.getTitle(), unitOf(a, posById), a.getPositionId(), a.isPrimary());
        }).sorted(Comparator.comparing((AssignmentView a) -> a.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        return new StructureView(unitViews, positionViews, assignmentViews, ctx.can(RoleCatalogue.STRUCTURE));
    }

    // ── Units ────────────────────────────────────────────────────────────────────────

    @Transactional
    public UnitView saveUnit(String slug, UUID id, UnitRequest in) {
        OrgContext ctx = manager(slug);
        OrgUnit u = id == null ? new OrgUnit() : unit(ctx, id);
        if (id != null && u.isArchived()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the unit first", List.of());
        }
        String name = in.name().strip();
        String code = clean(in.code());
        UUID self = id == null ? Ids.next() : id;
        if (in.parentId() != null) {
            OrgUnit parent = units.findByIdAndOrgId(in.parentId(), ctx.orgId()).filter(p -> !p.isArchived())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "parentId", "Choose an active unit"));
            if (id != null && isWithin(ctx.orgId(), parent.getId(), id)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "parentId", "A unit can't be placed under itself or its own sub-unit");
            }
        }
        if (units.nameTaken(ctx.orgId(), in.parentId(), name, self)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "There is already a unit with this name here");
        }
        if (code != null && units.codeTaken(ctx.orgId(), code, self)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "code", "This code is already used");
        }
        if (in.headUserId() != null && !active(ctx.orgId()).containsKey(in.headUserId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "headUserId", "Choose a member of the organisation");
        }
        boolean moved = id != null && (!Objects.equals(u.getParentId(), in.parentId()) || !u.getName().equals(name)
                || !u.getKind().equals(in.kind()));
        boolean newHead = !Objects.equals(u.getHeadUserId(), in.headUserId());
        Instant now = clock.instant();
        if (id == null) {
            u.setId(self);
            u.setOrgId(ctx.orgId());
            u.setCreatedAt(now);
        }
        u.setName(name);
        u.setKind(in.kind());
        u.setParentId(in.parentId());
        u.setCode(code);
        u.setHeadUserId(in.headUserId());
        u.setAddress(clean(in.address()));
        u.setPhone(clean(in.phone()));
        u.setUpdatedAt(now);
        units.save(u);
        audit(ctx, "unit", u.getId(), id == null ? "org.unit.created" : "org.unit.updated", Map.of("name", name, "kind", u.getKind()));
        if (moved || newHead) {
            // A new head becomes the default manager of the unit's people who have none yet.
            changed(ctx.orgId(), newHead ? memberUsers(ctx.orgId(), placedIn(ctx.orgId(), u.getId())) : List.of());
        }
        return unitView(ctx, u);
    }

    /**
     * Archives a unit that is no longer used (no active sub-units or positions, nobody placed in it) or restores
     * one (its parent must be active). Archived units keep their history but can't be chosen.
     */
    @Transactional
    public UnitView archiveUnit(String slug, UUID id, boolean archived) {
        OrgContext ctx = manager(slug);
        OrgUnit u = unit(ctx, id);
        if (archived == u.isArchived()) {
            return unitView(ctx, u);
        }
        if (archived) {
            inUse(ctx, u, true);
            u.setArchivedAt(clock.instant());
        } else {
            if (u.getParentId() != null && unit(ctx, u.getParentId()).isArchived()) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the unit it belongs to first", List.of());
            }
            if (units.nameTaken(ctx.orgId(), u.getParentId(), u.getName(), u.getId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "There is already a unit with this name here", List.of());
            }
            u.setArchivedAt(null);
        }
        u.setUpdatedAt(clock.instant());
        audit(ctx, "unit", u.getId(), archived ? "org.unit.archived" : "org.unit.restored", Map.of("name", u.getName()));
        return unitView(ctx, u);
    }

    /** Deletes a unit that was never used: no sub-units or positions (even archived ones) and nobody placed in it. */
    @Transactional
    public void deleteUnit(String slug, UUID id) {
        OrgContext ctx = manager(slug);
        OrgUnit u = unit(ctx, id);
        inUse(ctx, u, false);
        units.delete(u);
        audit(ctx, "unit", u.getId(), "org.unit.deleted", Map.of("name", u.getName()));
    }

    private void inUse(OrgContext ctx, OrgUnit u, boolean activeOnly) {
        if (units.findByOrgId(ctx.orgId()).stream().anyMatch(x -> u.getId().equals(x.getParentId()) && (!activeOnly || !x.isArchived()))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Move or archive its sub-units first", List.of());
        }
        if (positions.findByOrgId(ctx.orgId()).stream().anyMatch(p -> u.getId().equals(p.getUnitId()) && (!activeOnly || !p.isArchived()))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Move or archive its positions first", List.of());
        }
        if (assignments.existsByUnitId(u.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Move the people in it to another unit first", List.of());
        }
        if (roleGrants.existsByUnitId(u.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Remove the roles granted for it first", List.of());
        }
    }

    // ── Positions ────────────────────────────────────────────────────────────────────

    @Transactional
    public PositionView savePosition(String slug, UUID id, PositionRequest in) {
        OrgContext ctx = manager(slug);
        OrgPosition p = id == null ? new OrgPosition() : position(ctx, id);
        if (id != null && p.isArchived()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the position first", List.of());
        }
        UUID self = id == null ? Ids.next() : id;
        String title = in.title().strip();
        String code = clean(in.code());
        if (in.unitId() != null) {
            units.findByIdAndOrgId(in.unitId(), ctx.orgId()).filter(u -> !u.isArchived())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "unitId", "Choose an active unit"));
        }
        if (in.reportsToId() != null) {
            positions.findByIdAndOrgId(in.reportsToId(), ctx.orgId()).filter(x -> !x.isArchived())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "reportsToId", "Choose an active position"));
            if (reportsUpTo(ctx.orgId(), in.reportsToId(), self)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "reportsToId", "That position reports to this one already");
            }
        }
        if (positions.titleTaken(ctx.orgId(), in.unitId(), title, self)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "title", "There is already a position with this title here");
        }
        if (code != null && positions.codeTaken(ctx.orgId(), code, self)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "code", "This code is already used");
        }
        boolean retitled = id != null && !p.getTitle().equals(title);
        boolean moved = id != null && !Objects.equals(p.getUnitId(), in.unitId());
        Instant now = clock.instant();
        if (id == null) {
            p.setId(self);
            p.setOrgId(ctx.orgId());
            p.setCreatedAt(now);
        }
        p.setTitle(title);
        p.setUnitId(in.unitId());
        p.setCode(code);
        p.setGrade(clean(in.grade()));
        p.setReportsToId(in.reportsToId());
        p.setHeadcount(in.headcount());
        p.setDescription(clean(in.description()));
        p.setUpdatedAt(now);
        positions.save(p);
        List<OrgAssignment> held = id == null ? List.of() : assignments.findByPositionId(p.getId());
        if (moved) {
            // People holding it move with it: a position in a unit places its holders in that unit.
            held.forEach(a -> a.setUnitId(in.unitId()));
        }
        if (retitled) {
            Set<UUID> primary = held.stream().filter(OrgAssignment::isPrimary).map(OrgAssignment::getMembershipId).collect(Collectors.toSet());
            memberships.findAllById(primary).forEach(m -> m.setTitle(title));
        }
        audit(ctx, "position", p.getId(), id == null ? "org.position.created" : "org.position.updated", Map.of("title", title));
        if (moved || retitled) {
            changed(ctx.orgId(), List.of());
        }
        return positionView(p);
    }

    /** Archives a position nobody holds (or restores one; its unit must be active). */
    @Transactional
    public PositionView archivePosition(String slug, UUID id, boolean archived) {
        OrgContext ctx = manager(slug);
        OrgPosition p = position(ctx, id);
        if (archived == p.isArchived()) {
            return positionView(p);
        }
        if (archived) {
            if (assignments.existsByPositionId(p.getId())) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "Move the people holding it to another position first", List.of());
            }
            p.setArchivedAt(clock.instant());
        } else {
            if (p.getUnitId() != null && unit(ctx, p.getUnitId()).isArchived()) {
                throw new BusinessException(ErrorCode.INVALID_STATE, "Restore the unit it belongs to first", List.of());
            }
            if (positions.titleTaken(ctx.orgId(), p.getUnitId(), p.getTitle(), p.getId())) {
                throw new BusinessException(ErrorCode.CONFLICT, "There is already a position with this title here", List.of());
            }
            p.setArchivedAt(null);
        }
        p.setUpdatedAt(clock.instant());
        audit(ctx, "position", p.getId(), archived ? "org.position.archived" : "org.position.restored", Map.of("title", p.getTitle()));
        return positionView(p);
    }

    @Transactional
    public void deletePosition(String slug, UUID id) {
        OrgContext ctx = manager(slug);
        OrgPosition p = position(ctx, id);
        if (assignments.existsByPositionId(p.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Move the people holding it to another position first", List.of());
        }
        positions.findByOrgId(ctx.orgId()).stream().filter(x -> p.getId().equals(x.getReportsToId())).forEach(x -> x.setReportsToId(null));
        positions.delete(p);
        audit(ctx, "position", p.getId(), "org.position.deleted", Map.of("title", p.getTitle()));
    }

    // ── Placements ───────────────────────────────────────────────────────────────────

    /** Replaces where a member sits in the structure (Owners and Admins). */
    @Transactional
    public List<AssignmentView> place(String slug, UUID membershipId, PlacementsRequest in) {
        OrgContext ctx = manager(slug);
        Membership m = member(ctx, membershipId);
        List<Placement> extra = in.additional() == null ? List.of() : in.additional();
        replace(ctx, m, in.primary(), extra);
        return placements(ctx, m);
    }

    /**
     * HR's profile form: sets the member's primary unit and position (both empty removes it), keeping their
     * additional places. Without a position, {@code title} becomes their job title.
     */
    @Transactional
    public void placePrimary(OrgContext ctx, UUID userId, UUID unitId, UUID positionId, String title) {
        if (placePrimary(ctx, userId, unitId, positionId, title, false)) {
            changed(ctx.orgId(), List.of(userId));
        }
    }

    /**
     * {@link #placePrimary} without telling the modules (a bulk import tells them once, at the end, through
     * {@link #announce}). Whether the member's places changed.
     */
    @Transactional
    public boolean placePrimary(OrgContext ctx, UUID userId, UUID unitId, UUID positionId, String title, boolean quiet) {
        if (!ctx.can(RoleCatalogue.STRUCTURE) && !ctx.can(RoleCatalogue.HR)) {
            throw BusinessException.forbidden();
        }
        Membership m = memberships.findByOrgIdAndUserId(ctx.orgId(), userId).orElseThrow(BusinessException::notFound);
        List<Placement> extra = assignments.findByMembershipIdOrderByCreatedAt(m.getId()).stream().filter(a -> !a.isPrimary())
                .map(a -> new Placement(a.getUnitId(), a.getPositionId())).toList();
        Placement primary = unitId == null && positionId == null ? null : new Placement(unitId, positionId);
        boolean moved = replace(ctx, m, primary, extra, false);
        if (positionId == null) {
            m.setTitle(clean(title));
        }
        return moved;
    }

    /** Tells the modules that these members were placed (after a bulk import). */
    public void announce(UUID orgId, List<UUID> placed) {
        changed(orgId, placed);
    }

    private void replace(OrgContext ctx, Membership m, Placement primary, List<Placement> extra) {
        replace(ctx, m, primary, extra, true);
    }

    private boolean replace(OrgContext ctx, Membership m, Placement primary, List<Placement> extra, boolean tell) {
        Map<UUID, OrgUnit> us = units.findByOrgId(ctx.orgId()).stream().collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Map<UUID, OrgPosition> ps = positions.findByOrgId(ctx.orgId()).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        List<OrgAssignment> before = assignments.findByMembershipIdOrderByCreatedAt(m.getId());
        Map<List<UUID>, Boolean> wanted = new LinkedHashMap<>();
        if (primary != null) {
            wanted.put(resolve(primary, us, ps, before, "primary"), true);
        }
        for (Placement p : extra) {
            wanted.putIfAbsent(resolve(p, us, ps, before, "additional"), false);
        }
        Map<List<UUID>, Boolean> had = new LinkedHashMap<>();
        before.forEach(a -> had.put(java.util.Arrays.asList(a.getUnitId(), a.getPositionId()), a.isPrimary()));
        if (had.equals(wanted)) {
            return false;
        }
        assignments.deleteByMembershipId(m.getId());
        assignments.flush();
        Instant now = clock.instant();
        wanted.forEach((k, isPrimary) -> {
            OrgAssignment a = new OrgAssignment();
            a.setId(Ids.next());
            a.setOrgId(ctx.orgId());
            a.setMembershipId(m.getId());
            a.setUnitId(k.get(0));
            a.setPositionId(k.get(1));
            a.setPrimary(isPrimary);
            a.setCreatedAt(now);
            assignments.save(a);
        });
        if (primary != null && primary.positionId() != null) {
            m.setTitle(ps.get(primary.positionId()).getTitle());
        }
        AppUser who = users.findById(m.getUserId()).orElseThrow();
        OrgPosition pos = primary == null || primary.positionId() == null ? null : ps.get(primary.positionId());
        UUID unitId = primary == null ? null : wanted.keySet().iterator().next().get(0);
        Map<String, Object> data = new HashMap<>(Map.of("name", who.getName()));
        if (pos != null) {
            data.put("position", pos.getTitle());
        }
        if (unitId != null) {
            data.put("unit", us.get(unitId).getName());
        }
        audit(ctx, "member", m.getUserId(), "org.member.placed", data);
        if (tell) {
            changed(ctx.orgId(), List.of(m.getUserId()));
        }
        return true;
    }

    /** [unitId, positionId] for a placement: the position's unit when it has one; unknown or archived ones refused. */
    private static List<UUID> resolve(Placement p, Map<UUID, OrgUnit> us, Map<UUID, OrgPosition> ps, List<OrgAssignment> before,
            String field) {
        // An archived unit or position the member already sits in may stay; nothing archived can be newly chosen.
        Set<UUID> kept = before.stream().flatMap(a -> java.util.stream.Stream.of(a.getUnitId(), a.getPositionId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        UUID unitId = p.unitId();
        if (p.positionId() != null) {
            OrgPosition pos = ps.get(p.positionId());
            if (pos == null || (pos.isArchived() && !kept.contains(pos.getId()))) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "Choose an active position");
            }
            if (pos.getUnitId() != null) {
                if (unitId != null && !unitId.equals(pos.getUnitId())) {
                    throw BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "That position belongs to another unit");
                }
                unitId = pos.getUnitId();
            }
        }
        if (unitId != null) {
            OrgUnit u = us.get(unitId);
            if (u == null || (u.isArchived() && !kept.contains(u.getId()))) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "Choose an active unit");
            }
        }
        if (unitId == null && p.positionId() == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "Choose a unit or a position");
        }
        return java.util.Arrays.asList(unitId, p.positionId());
    }

    @Transactional(readOnly = true)
    public List<AssignmentView> placements(String slug, UUID membershipId) {
        OrgContext ctx = reader(slug);
        return placements(ctx, member(ctx, membershipId));
    }

    private List<AssignmentView> placements(OrgContext ctx, Membership m) {
        Map<UUID, OrgPosition> ps = positions.findByOrgId(ctx.orgId()).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        Map<UUID, AppUser> people = people(List.of(m.getUserId()));
        return assignments.findByMembershipIdOrderByCreatedAt(m.getId()).stream()
                .sorted(Comparator.comparing((OrgAssignment a) -> !a.isPrimary()))
                .map(a -> new AssignmentView(a.getId(), brief(m, people), m.getTitle(), unitOf(a, ps), a.getPositionId(), a.isPrimary()))
                .toList();
    }

    // ── For the rest of the platform ─────────────────────────────────────────────────

    /** Each member's primary unit name (membership id → name), for lists of members. */
    @Transactional(readOnly = true)
    public Map<UUID, String> primaryUnitNames(UUID orgId) {
        Map<UUID, OrgUnit> us = units.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Map<UUID, OrgPosition> ps = positions.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        Map<UUID, String> out = new HashMap<>();
        assignments.findByOrgId(orgId).stream().filter(OrgAssignment::isPrimary).forEach(a -> {
            UUID unit = unitOf(a, ps);
            if (unit != null && us.containsKey(unit)) {
                out.put(a.getMembershipId(), us.get(unit).getName());
            }
        });
        return out;
    }

    /**
     * Who a member reports to by the structure: an active holder of their primary position's "reports to"
     * position, else the head of their primary unit or the nearest unit above it with a head (never themselves).
     */
    @Transactional(readOnly = true)
    public Optional<UUID> structuralManager(UUID orgId, UUID userId) {
        Map<UUID, Membership> active = active(orgId);
        Membership m = active.get(userId);
        if (m == null) {
            return Optional.empty();
        }
        OrgAssignment primary = assignments.findByMembershipIdOrderByCreatedAt(m.getId()).stream().filter(OrgAssignment::isPrimary)
                .findFirst().orElse(null);
        if (primary == null) {
            return Optional.empty();
        }
        Map<UUID, OrgPosition> ps = positions.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        Map<UUID, UUID> userOfMembership = active.values().stream().collect(Collectors.toMap(Membership::getId, Membership::getUserId));
        OrgPosition pos = primary.getPositionId() == null ? null : ps.get(primary.getPositionId());
        if (pos != null && pos.getReportsToId() != null) {
            Optional<UUID> boss = assignments.findByPositionId(pos.getReportsToId()).stream()
                    .sorted(Comparator.comparing((OrgAssignment a) -> !a.isPrimary()).thenComparing(OrgAssignment::getCreatedAt))
                    .map(a -> userOfMembership.get(a.getMembershipId())).filter(Objects::nonNull).filter(u -> !u.equals(userId)).findFirst();
            if (boss.isPresent()) {
                return boss;
            }
        }
        Map<UUID, OrgUnit> us = units.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgUnit::getId, Function.identity()));
        Set<UUID> seen = new HashSet<>();
        for (UUID at = unitOf(primary, ps); at != null && seen.add(at); at = us.get(at) == null ? null : us.get(at).getParentId()) {
            UUID head = us.get(at) == null ? null : us.get(at).getHeadUserId();
            if (head != null && !head.equals(userId) && active.containsKey(head)) {
                return Optional.of(head);
            }
        }
        return Optional.empty();
    }

    /** The unit a position belongs to, if it has one. */
    @Transactional(readOnly = true)
    public Optional<UUID> positionUnit(UUID orgId, UUID positionId) {
        return positions.findByIdAndOrgId(positionId, orgId).map(OrgPosition::getUnitId);
    }

    /** The member's primary place, if any (unit resolved from the position when only that is set). */
    @Transactional(readOnly = true)
    public Optional<Placement> primaryOf(UUID orgId, UUID membershipId) {
        Map<UUID, OrgPosition> ps = positions.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        return assignments.findByMembershipIdOrderByCreatedAt(membershipId).stream().filter(OrgAssignment::isPrimary).findFirst()
                .map(a -> new Placement(unitOf(a, ps), a.getPositionId()));
    }

    /**
     * The active DEPARTMENT unit with this name (a top-level one first), created at the top level when there is
     * none: for clients that still send a department as text.
     */
    @Transactional
    public UUID departmentNamed(OrgContext ctx, String name) {
        if (!ctx.can(RoleCatalogue.STRUCTURE) && !ctx.can(RoleCatalogue.HR)) {
            throw BusinessException.forbidden();
        }
        String wanted = name.strip();
        return units.findByOrgId(ctx.orgId()).stream()
                .filter(u -> !u.isArchived() && "DEPARTMENT".equals(u.getKind()) && u.getName().equalsIgnoreCase(wanted))
                .sorted(Comparator.comparing((OrgUnit u) -> u.getParentId() != null)).map(OrgUnit::getId).findFirst()
                .orElseGet(() -> {
                    if (units.nameTaken(ctx.orgId(), null, wanted, ctx.orgId())) {
                        throw BusinessException.field(ErrorCode.CONFLICT, "department", "There is already a unit with this name here");
                    }
                    Instant now = clock.instant();
                    OrgUnit u = new OrgUnit();
                    u.setId(Ids.next());
                    u.setOrgId(ctx.orgId());
                    u.setKind("DEPARTMENT");
                    u.setName(wanted);
                    u.setCreatedAt(now);
                    u.setUpdatedAt(now);
                    units.save(u);
                    audit(ctx, "unit", u.getId(), "org.unit.created", Map.of("name", wanted, "kind", u.getKind()));
                    return u.getId();
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private OrgContext manager(String slug) {
        OrgContext ctx = reader(slug);
        if (!ctx.can(RoleCatalogue.STRUCTURE)) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private OrgContext reader(String slug) {
        OrgContext ctx = access.require(slug);
        if (ctx.isGuest()) {
            throw BusinessException.forbidden();
        }
        return ctx;
    }

    private OrgUnit unit(OrgContext ctx, UUID id) {
        return units.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private OrgPosition position(OrgContext ctx, UUID id) {
        return positions.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** An active non-guest member of the organisation. */
    private Membership member(OrgContext ctx, UUID membershipId) {
        return memberships.findById(membershipId).filter(x -> x.getOrgId().equals(ctx.orgId()))
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()) && x.getRole() != OrgRole.GUEST)
                .orElseThrow(BusinessException::notFound);
    }

    /** Active non-guest members by user id. */
    private Map<UUID, Membership> active(UUID orgId) {
        return memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST)
                .collect(Collectors.toMap(Membership::getUserId, Function.identity()));
    }

    /** Whether {@code unitId} is {@code ancestor} or sits somewhere below it. */
    private boolean isWithin(UUID orgId, UUID unitId, UUID ancestor) {
        Map<UUID, UUID> up = new HashMap<>();
        units.findByOrgId(orgId).forEach(u -> up.put(u.getId(), u.getParentId()));
        Set<UUID> seen = new HashSet<>();
        for (UUID at = unitId; at != null && seen.add(at); at = up.get(at)) {
            if (at.equals(ancestor)) {
                return true;
            }
        }
        return false;
    }

    /** Whether position {@code from} is {@code target} or reports (directly or not) to it. */
    private boolean reportsUpTo(UUID orgId, UUID from, UUID target) {
        Map<UUID, UUID> up = new HashMap<>();
        positions.findByOrgId(orgId).forEach(p -> up.put(p.getId(), p.getReportsToId()));
        Set<UUID> seen = new HashSet<>();
        for (UUID at = from; at != null && seen.add(at); at = up.get(at)) {
            if (at.equals(target)) {
                return true;
            }
        }
        return false;
    }

    private List<UUID> placedIn(UUID orgId, UUID unitId) {
        Map<UUID, OrgPosition> ps = positions.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        return assignments.findByOrgId(orgId).stream().filter(a -> a.isPrimary() && unitId.equals(unitOf(a, ps)))
                .map(OrgAssignment::getMembershipId).toList();
    }

    private List<UUID> memberUsers(UUID orgId, Collection<UUID> membershipIds) {
        return memberships.findAllById(membershipIds).stream().filter(m -> m.getOrgId().equals(orgId)).map(Membership::getUserId).toList();
    }

    private static UUID unitOf(OrgAssignment a, Map<UUID, OrgPosition> ps) {
        if (a.getUnitId() != null) {
            return a.getUnitId();
        }
        OrgPosition p = a.getPositionId() == null ? null : ps.get(a.getPositionId());
        return p == null ? null : p.getUnitId();
    }

    private UnitView unitView(OrgContext ctx, OrgUnit u) {
        Map<UUID, Membership> active = active(ctx.orgId());
        Map<UUID, AppUser> people = people(u.getHeadUserId() == null ? List.of() : List.of(u.getHeadUserId()));
        Set<UUID> activeIds = active.values().stream().map(Membership::getId).collect(Collectors.toSet());
        int count = (int) new HashSet<>(placedInAny(ctx.orgId(), u.getId())).stream().filter(activeIds::contains).count();
        return new UnitView(u.getId(), u.getParentId(), u.getKind(), u.getName(), u.getCode(), brief(active.get(u.getHeadUserId()), people),
                u.getAddress(), u.getPhone(), u.isArchived(), count);
    }

    private List<UUID> placedInAny(UUID orgId, UUID unitId) {
        Map<UUID, OrgPosition> ps = positions.findByOrgId(orgId).stream().collect(Collectors.toMap(OrgPosition::getId, Function.identity()));
        return assignments.findByOrgId(orgId).stream().filter(a -> unitId.equals(unitOf(a, ps))).map(OrgAssignment::getMembershipId).toList();
    }

    private PositionView positionView(OrgPosition p) {
        Set<UUID> activeIds = active(p.getOrgId()).values().stream().map(Membership::getId).collect(Collectors.toSet());
        int filled = (int) assignments.findByPositionId(p.getId()).stream().filter(a -> activeIds.contains(a.getMembershipId())).count();
        return new PositionView(p.getId(), p.getUnitId(), p.getTitle(), p.getCode(), p.getGrade(), p.getReportsToId(), p.getHeadcount(),
                filled, p.getDescription(), p.isArchived());
    }

    private Map<UUID, AppUser> people(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        return wanted.isEmpty() ? Map.of()
                : users.findByIdIn(wanted).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private static PersonBrief brief(Membership m, Map<UUID, AppUser> people) {
        AppUser u = m == null ? null : people.get(m.getUserId());
        return u == null ? null : new PersonBrief(u.getId(), m.getId(), u.getName(), u.getAvatarUrl());
    }

    private void changed(UUID orgId, List<UUID> placed) {
        events.publishEvent(new StructureChanged(orgId, placed));
    }

    private void audit(OrgContext ctx, String recordType, UUID recordId, String verb, Map<String, Object> data) {
        activity.record(ctx.orgId(), "org", recordType, recordId, ctx.userId(), verb, data);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
