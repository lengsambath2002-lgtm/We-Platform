package com.businessplatform.org.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.i18n.Messages;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.identity.service.AccountService;
import com.businessplatform.org.domain.Invitation;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgAssignment;
import com.businessplatform.org.domain.OrgPosition;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.domain.OrgUnit;
import com.businessplatform.org.payload.ImportPayloads.*;
import com.businessplatform.org.payload.OrgPayloads.InviteRequest;
import com.businessplatform.org.payload.OrgPayloads.UpdateMemberRequest;
import com.businessplatform.org.payload.StructurePayloads.Placement;
import com.businessplatform.org.repository.InvitationRepository;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgAssignmentRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgPositionRepository;
import com.businessplatform.org.repository.StructureRepositories.OrgUnitRepository;

import lombok.RequiredArgsConstructor;

/**
 * Bulk import of people with their place in the structure (Owners and Admins): a spreadsheet's rows are checked
 * first (preview) and applied only when no row has a problem, all at once. Missing units and positions are created;
 * people without an account get one and an email to choose a password; people with an account elsewhere are
 * invited (import again after they accept to place them). Importing the same file twice changes nothing more.
 */
@Service
@RequiredArgsConstructor
public class OrgImportService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> EMPLOYMENT = Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN");

    private final OrgAccess access;
    private final OrgService orgs;
    private final OrgStructureService structure;
    private final AccountService accounts;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final OrgUnitRepository units;
    private final OrgPositionRepository positions;
    private final OrgAssignmentRepository assignments;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;
    private final Messages messages;
    private final Clock clock;

    /** One checked row, with what applying it needs. */
    private static final class Line {
        int row;
        ImportRow in;
        String email;
        String name;
        String action;
        OrgRole role;
        AppUser user;
        Membership member;
        List<String> path = List.of();
        List<String> kinds = List.of();
        String position;
        String managerEmail;
        String secondEmail;
        String employment;
        LocalDate start;
        final List<String> problems = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
    }

    @Transactional(readOnly = true)
    public ImportResult preview(String slug, ImportRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        Plan plan = plan(ctx, in.rows());
        return plan.result(false);
    }

    @Transactional
    public ImportResult apply(String slug, ImportRequest in) {
        OrgContext ctx = access.requireAdmin(slug);
        Plan plan = plan(ctx, in.rows());
        if (plan.errors() > 0) {
            return plan.result(false);
        }
        execute(ctx, slug, plan);
        return plan.result(true);
    }

    // ── Checking ─────────────────────────────────────────────────────────────────────

    private record Plan(List<Line> lines, List<String> newUnits, List<String> newPositions) {

        int errors() {
            return (int) lines.stream().filter(l -> !l.problems.isEmpty()).count();
        }

        ImportResult result(boolean applied) {
            List<RowResult> rows = lines.stream().map(l -> new RowResult(l.row, l.email, l.name,
                    l.problems.isEmpty() ? l.action : "ERROR", l.problems, l.notes,
                    l.path.isEmpty() ? null : String.join(" › ", l.path), l.position, l.managerEmail)).toList();
            return new ImportResult(applied, rows, count("CREATE"), count("INVITE"), count("UPDATE"), errors(), newUnits, newPositions);
        }

        private int count(String action) {
            return (int) lines.stream().filter(l -> l.problems.isEmpty() && action.equals(l.action)).count();
        }
    }

    private Plan plan(OrgContext ctx, List<ImportRow> rows) {
        Map<String, Membership> byEmail = new HashMap<>();
        List<Membership> ms = memberships.findByOrgIdOrderByCreatedAt(ctx.orgId());
        Map<UUID, Membership> byUser = new HashMap<>();
        ms.forEach(m -> byUser.put(m.getUserId(), m));
        users.findByIdIn(byUser.keySet()).forEach(u -> byEmail.put(u.getEmail(), byUser.get(u.getId())));
        Set<String> invited = new HashSet<>();
        invitations.findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(ctx.orgId()).stream()
                .map(Invitation::getEmail).forEach(invited::add);

        List<Line> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            ImportRow r = rows.get(i);
            Line l = new Line();
            l.row = i + 1;
            l.in = r;
            l.email = lower(r.email());
            l.name = clean(r.name());
            if (l.email == null || !EMAIL.matcher(l.email).matches()) {
                l.problems.add(messages.text("Enter a valid email"));
            } else if (!seen.add(l.email)) {
                l.problems.add(messages.text("This email is in the file twice"));
            }
            String role = upper(r.role());
            if (role != null && !Set.of("MEMBER", "ADMIN", "GUEST").contains(role)) {
                l.problems.add(messages.text("Role is Member, Admin or Guest"));
            } else {
                l.role = role == null ? null : OrgRole.valueOf(role);
            }
            if (l.email != null && l.problems.isEmpty()) {
                l.member = byEmail.get(l.email);
                l.user = l.member != null ? null : users.findByEmail(l.email).orElse(null);
                if (l.member != null) {
                    l.action = "UPDATE";
                    if (l.member.getRole() == OrgRole.OWNER && l.role != null) {
                        l.notes.add(messages.text("An owner's role isn't changed by an import"));
                        l.role = null;
                    }
                } else if (l.user != null || invited.contains(l.email)) {
                    l.action = "INVITE";
                } else {
                    l.action = "CREATE";
                    if (l.name == null) {
                        l.problems.add(messages.text("Enter the person's name"));
                    }
                }
            }
            l.path = split(r.unitPath());
            l.kinds = split(r.unitKinds()).stream().map(k -> k.toUpperCase(Locale.ROOT)).toList();
            if (l.path.stream().anyMatch(p -> p.length() > 120)) {
                l.problems.add(messages.text("Unit names are at most 120 characters"));
            }
            if (l.kinds.stream().anyMatch(k -> !OrgUnit.KINDS.contains(k))) {
                l.problems.add(messages.text("Unit types are Branch, Division, Department, Center, Section or Team"));
            }
            l.position = clean(r.position());
            OrgRole effective = l.role != null ? l.role : l.member != null ? l.member.getRole() : OrgRole.MEMBER;
            if (effective == OrgRole.GUEST && (!l.path.isEmpty() || l.position != null)) {
                l.problems.add(messages.text("Guests can't be placed in the structure"));
            }
            l.managerEmail = lower(r.managerEmail());
            l.secondEmail = lower(r.secondManagerEmail());
            if (l.email != null && (l.email.equals(l.managerEmail) || l.email.equals(l.secondEmail))) {
                l.problems.add(messages.text("Someone can't be their own manager"));
            }
            l.employment = upper(r.employmentType());
            if (l.employment != null && !EMPLOYMENT.contains(l.employment)) {
                l.problems.add(messages.text("Employment is FULL_TIME, PART_TIME, CONTRACT or INTERN"));
            }
            String start = clean(r.startDate());
            if (start != null) {
                try {
                    l.start = LocalDate.parse(start);
                } catch (DateTimeParseException e) {
                    l.problems.add(messages.text("Dates are written 2026-01-31"));
                }
            }
            if ("INVITE".equals(l.action) && (!l.path.isEmpty() || l.position != null || l.managerEmail != null)) {
                l.notes.add(messages.text("Placed when you import again after they accept the invitation"));
            }
            lines.add(l);
        }

        // Managers: someone in the file who will be a member, or a member already; never a loop.
        Map<String, Line> inFile = new HashMap<>();
        lines.forEach(l -> {
            if (l.email != null) {
                inFile.putIfAbsent(l.email, l);
            }
        });
        for (Line l : lines) {
            for (String m : java.util.Arrays.asList(l.managerEmail, l.secondEmail)) {
                if (m == null) {
                    continue;
                }
                Line other = inFile.get(m);
                boolean member = byEmail.containsKey(m) && Membership.ACTIVE.equals(byEmail.get(m).getStatus());
                if (other == null && !member) {
                    l.problems.add(messages.text("{email} is neither in the file nor a member", Map.of("email", m)));
                } else if (other != null && "INVITE".equals(other.action) && !member) {
                    l.notes.add(messages.text("{email} isn't a member yet: set after they join", Map.of("email", m)));
                }
            }
        }
        for (Line l : lines) {
            Set<String> chain = new HashSet<>();
            for (String at = l.email; at != null && inFile.containsKey(at); at = inFile.get(at).managerEmail) {
                if (!chain.add(at)) {
                    l.problems.add(messages.text("Managers go round in a circle"));
                    break;
                }
            }
        }

        // What the structure will gain.
        Map<String, OrgUnit> existing = new HashMap<>();
        Map<UUID, OrgUnit> unitById = new HashMap<>();
        units.findByOrgId(ctx.orgId()).stream().filter(u -> !u.isArchived()).forEach(u -> unitById.put(u.getId(), u));
        unitById.values().forEach(u -> existing.put(pathKey(unitById, u), u));
        Set<String> newUnits = new LinkedHashSet<>();
        Set<String> newPositions = new LinkedHashSet<>();
        Set<String> heldPositions = new HashSet<>();
        positions.findByOrgId(ctx.orgId()).stream().filter(p -> !p.isArchived())
                .forEach(p -> heldPositions.add((p.getUnitId() == null ? "" : pathKey(unitById, unitById.get(p.getUnitId()))) + "#" + p.getTitle().toLowerCase(Locale.ROOT)));
        for (Line l : lines) {
            if (!l.problems.isEmpty() || "INVITE".equals(l.action)) {
                continue;
            }
            for (int d = 1; d <= l.path.size(); d++) {
                String key = key(l.path.subList(0, d));
                if (!existing.containsKey(key)) {
                    newUnits.add(String.join(" › ", l.path.subList(0, d)));
                }
            }
            if (l.position != null && !heldPositions.contains(key(l.path) + "#" + l.position.toLowerCase(Locale.ROOT))) {
                newPositions.add(l.path.isEmpty() ? l.position : l.position + " · " + String.join(" › ", l.path));
            }
        }
        return new Plan(lines, List.copyOf(newUnits), List.copyOf(newPositions));
    }

    // ── Applying ─────────────────────────────────────────────────────────────────────

    private void execute(OrgContext ctx, String slug, Plan plan) {
        String by = users.findById(ctx.userId()).map(AppUser::getName).orElse("");
        Map<String, UUID> userOf = new HashMap<>();
        memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).forEach(m -> users.findById(m.getUserId())
                .filter(u -> Membership.ACTIVE.equals(m.getStatus())).ifPresent(u -> userOf.put(u.getEmail(), u.getId())));
        Instant now = clock.instant();
        for (Line l : plan.lines()) {
            switch (l.action) {
                case "CREATE" -> {
                    AppUser u = accounts.provision(l.email, l.name, Messages.requestLanguage(), ctx.orgName(), by);
                    Membership m = new Membership();
                    m.setId(Ids.next());
                    m.setOrgId(ctx.orgId());
                    m.setUserId(u.getId());
                    m.setRole(l.role == null ? OrgRole.MEMBER : l.role);
                    m.setCreatedAt(now);
                    l.member = memberships.save(m);
                    userOf.put(l.email, u.getId());
                    activity.record(ctx.orgId(), "org", "member", u.getId(), ctx.userId(), "org.member.joined",
                            Map.of("name", u.getName(), "role", m.getRole().name()));
                }
                case "INVITE" -> {
                    boolean pending = invitations.findByOrgIdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc(ctx.orgId())
                            .stream().anyMatch(i -> i.getEmail().equals(l.email));
                    if (!pending) {
                        orgs.invite(slug, new InviteRequest(l.email, l.role == null || l.role == OrgRole.OWNER ? OrgRole.MEMBER : l.role));
                    }
                }
                default -> {
                    if (l.role != null && l.role != l.member.getRole() && !l.member.getUserId().equals(ctx.userId())) {
                        orgs.updateMember(slug, l.member.getId(), new UpdateMemberRequest(l.role, null, null));
                    }
                }
            }
        }

        // Units, positions and places.
        Map<UUID, OrgUnit> unitById = new HashMap<>();
        units.findByOrgId(ctx.orgId()).stream().filter(u -> !u.isArchived()).forEach(u -> unitById.put(u.getId(), u));
        Map<String, UUID> unitAt = new HashMap<>();
        unitById.values().forEach(u -> unitAt.put(pathKey(unitById, u), u.getId()));
        Set<UUID> touched = new HashSet<>();
        List<UUID> placed = new ArrayList<>();
        Map<UUID, UUID> heads = new LinkedHashMap<>();
        for (Line l : plan.lines()) {
            if ("INVITE".equals(l.action) || (l.path.isEmpty() && l.position == null)) {
                continue;
            }
            UUID unitId = null;
            for (int d = 1; d <= l.path.size(); d++) {
                String key = key(l.path.subList(0, d));
                UUID parent = unitId;
                String kind = d - 1 < l.kinds.size() ? l.kinds.get(d - 1) : d == l.path.size() && d > 1 ? "TEAM" : "DEPARTMENT";
                String name = l.path.get(d - 1);
                unitId = unitAt.computeIfAbsent(key, k -> {
                    OrgUnit u = new OrgUnit();
                    u.setId(Ids.next());
                    u.setOrgId(ctx.orgId());
                    u.setParentId(parent);
                    u.setKind(kind);
                    u.setName(name);
                    u.setCreatedAt(now);
                    u.setUpdatedAt(now);
                    units.save(u);
                    unitById.put(u.getId(), u);
                    return u.getId();
                });
            }
            UUID positionId = null;
            if (l.position != null) {
                UUID in = unitId;
                positionId = positions.findByOrgId(ctx.orgId()).stream()
                        .filter(p -> !p.isArchived() && java.util.Objects.equals(p.getUnitId(), in) && p.getTitle().equalsIgnoreCase(l.position))
                        .map(OrgPosition::getId).findFirst().orElseGet(() -> {
                            OrgPosition p = new OrgPosition();
                            p.setId(Ids.next());
                            p.setOrgId(ctx.orgId());
                            p.setUnitId(in);
                            p.setTitle(l.position);
                            p.setHeadcount(0);
                            p.setCreatedAt(now);
                            p.setUpdatedAt(now);
                            positions.saveAndFlush(p);
                            return p.getId();
                        });
                touched.add(positionId);
            }
            Membership m = l.member;
            UUID wantUnit = unitId;
            UUID wantPosition = positionId;
            if (positionId == null) {
                // Only a unit: keep their position if it belongs to that unit.
                UUID in = unitId;
                wantPosition = structure.primaryOf(ctx.orgId(), m.getId()).map(Placement::positionId)
                        .filter(x -> java.util.Objects.equals(structure.positionUnit(ctx.orgId(), x).orElse(null), in)).orElse(null);
            } else if (unitId == null) {
                wantUnit = structure.positionUnit(ctx.orgId(), positionId).orElse(null);
            }
            if (structure.placePrimary(ctx, m.getUserId(), wantUnit, wantPosition, m.getTitle(), true)) {
                placed.add(m.getUserId());
            }
            if (Boolean.TRUE.equals(l.in.unitHead()) && unitId != null) {
                heads.put(unitId, m.getUserId());
            }
        }
        heads.forEach((unit, head) -> units.findById(unit).ifPresent(u -> u.setHeadUserId(head)));
        // A position is planned for at least as many people as hold it.
        for (UUID p : touched) {
            OrgPosition pos = positions.findById(p).orElseThrow();
            int held = (int) assignments.findByPositionId(p).stream().map(OrgAssignment::getMembershipId).distinct().count();
            if (held > pos.getHeadcount()) {
                pos.setHeadcount(held);
            }
        }

        // The modules hear about the new places once (HR then copies departments and default managers).
        if (!placed.isEmpty()) {
            structure.announce(ctx.orgId(), placed);
        }
        // HR's part: managers, 2nd approvers, employment.
        List<PeopleImported.Person> people = new ArrayList<>();
        for (Line l : plan.lines()) {
            UUID who = userOf.get(l.email);
            if (who == null || "INVITE".equals(l.action)) {
                continue;
            }
            UUID manager = l.managerEmail == null ? null : userOf.get(l.managerEmail);
            UUID second = l.secondEmail == null ? null : userOf.get(l.secondEmail);
            if (manager != null || second != null || l.employment != null || l.start != null) {
                people.add(new PeopleImported.Person(who, manager, second, l.employment, l.start));
            }
        }
        if (!people.isEmpty()) {
            events.publishEvent(new PeopleImported(ctx.orgId(), people));
        }
        activity.record(ctx.orgId(), "org", "organisation", ctx.orgId(), ctx.userId(), "org.people.imported",
                Map.of("rows", plan.lines().size(), "created", plan.result(true).creates(), "invited", plan.result(true).invites(),
                        "units", plan.newUnits().size(), "positions", plan.newPositions().size()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private static List<String> split(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(path.split("[>›/]")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String key(List<String> path) {
        return String.join("\u0000", path).toLowerCase(Locale.ROOT);
    }

    /** The unit's path from the top, as a lookup key. */
    private static String pathKey(Map<UUID, OrgUnit> byId, OrgUnit u) {
        List<String> names = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (OrgUnit at = u; at != null && seen.add(at.getId()); at = at.getParentId() == null ? null : byId.get(at.getParentId())) {
            names.add(0, at.getName());
        }
        return key(names);
    }

    private static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String lower(String s) {
        String c = clean(s);
        return c == null ? null : c.toLowerCase(Locale.ROOT);
    }

    private static String upper(String s) {
        String c = clean(s);
        return c == null ? null : c.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
