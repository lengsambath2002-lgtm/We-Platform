package com.businessplatform.hr.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.payload.HrPayloads.*;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Employee profiles and the people directory. Every active non-guest member has a profile (created on
 * first use). Everyone sees name, title, department, manager and work phone; employment details and the
 * emergency contact only the person and HR managers. HR managers edit everything; people edit their
 * own phone and emergency contact.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    /** How profiles appear in the activity log. */
    public static final String RECORD = "employee";

    private final HrAccess hr;
    private final EmployeeProfileRepository profiles;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional
    public List<PersonSummary> directory(String slug) {
        OrgContext ctx = hr.require(slug);
        profiles.createMissing(ctx.orgId());
        Map<UUID, Membership> active = activeMembers(ctx.orgId());
        List<EmployeeProfile> ps = profiles.findByOrgId(ctx.orgId()).stream()
                .filter(p -> active.containsKey(p.getUserId())).toList();
        Map<UUID, AppUser> people = users(ps.stream().flatMap(p -> java.util.stream.Stream.of(p.getUserId(), p.getManagerId())).toList());
        return ps.stream().map(p -> {
            AppUser u = people.get(p.getUserId());
            return new PersonSummary(ref(u), u.getEmail(), active.get(p.getUserId()).getTitle(), p.getDepartment(),
                    activeManager(p, active).map(people::get).map(ProfileService::ref).orElse(null), p.getWorkPhone());
        }).sorted(Comparator.comparing(s -> s.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional
    public ProfileView get(String slug, UUID userId) {
        OrgContext ctx = hr.require(slug);
        return view(ctx, profile(ctx, userId));
    }

    @Transactional
    public ProfileView update(String slug, UUID userId, ProfileRequest in) {
        OrgContext ctx = hr.require(slug);
        EmployeeProfile p = profile(ctx, userId);
        if (!HrAccess.manages(ctx)) {
            throw BusinessException.forbidden();
        }
        Membership m = memberships.findByOrgIdAndUserId(ctx.orgId(), userId).orElseThrow();
        UUID manager = in.managerId();
        if (manager != null && !manager.equals(p.getManagerId())) {
            if (manager.equals(userId)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "managerId", "Someone can't be their own manager");
            }
            if (!activeMembers(ctx.orgId()).containsKey(manager)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "managerId", "Choose a member of the organisation");
            }
            if (reportsTo(ctx.orgId(), manager, userId)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "managerId", "That person reports to them already");
            }
        }
        List<String> changed = new ArrayList<>();
        change(changed, "title", m.getTitle(), clean(in.title()), m::setTitle);
        change(changed, "department", p.getDepartment(), clean(in.department()), p::setDepartment);
        change(changed, "manager", p.getManagerId(), manager, p::setManagerId);
        change(changed, "employmentType", p.getEmploymentType(), clean(in.employmentType()), p::setEmploymentType);
        change(changed, "startDate", p.getStartDate(), in.startDate(), p::setStartDate);
        contact(changed, p, in.workPhone(), in.emergencyName(), in.emergencyPhone(), in.emergencyRelation());
        saved(ctx, p, changed);
        return view(ctx, p);
    }

    /** The caller's own phone and emergency contact. */
    @Transactional
    public ProfileView updateContact(String slug, ContactRequest in) {
        OrgContext ctx = hr.require(slug);
        EmployeeProfile p = profile(ctx, ctx.userId());
        List<String> changed = new ArrayList<>();
        contact(changed, p, in.workPhone(), in.emergencyName(), in.emergencyPhone(), in.emergencyRelation());
        saved(ctx, p, changed);
        return view(ctx, p);
    }

    // ── For the rest of HR ───────────────────────────────────────────────────────────

    /** The person's profile, created if missing; NOT_FOUND unless they are an active non-guest member. */
    @Transactional
    public EmployeeProfile profile(OrgContext ctx, UUID userId) {
        memberships.findByOrgIdAndUserId(ctx.orgId(), userId)
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()) && x.getRole() != OrgRole.GUEST)
                .orElseThrow(BusinessException::notFound);
        return profiles.findByOrgIdAndUserId(ctx.orgId(), userId).orElseGet(() -> {
            profiles.createMissing(ctx.orgId());
            return profiles.findByOrgIdAndUserId(ctx.orgId(), userId).orElseThrow();
        });
    }

    /** The person's manager when one is set and still an active member. */
    @Transactional(readOnly = true)
    public Optional<UUID> managerOf(UUID orgId, UUID userId) {
        return profiles.findByOrgIdAndUserId(orgId, userId).map(EmployeeProfile::getManagerId)
                .filter(m -> memberships.existsByOrgIdAndUserIdAndStatus(orgId, m, Membership.ACTIVE));
    }

    @Transactional(readOnly = true)
    public Map<UUID, PersonRef> persons(Collection<UUID> ids) {
        return users(ids).values().stream().collect(Collectors.toMap(AppUser::getId, ProfileService::ref));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private ProfileView view(OrgContext ctx, EmployeeProfile p) {
        Map<UUID, Membership> active = activeMembers(ctx.orgId());
        List<UUID> reportIds = profiles.findByOrgId(ctx.orgId()).stream()
                .filter(x -> p.getUserId().equals(x.getManagerId()) && active.containsKey(x.getUserId()))
                .map(EmployeeProfile::getUserId).toList();
        Optional<UUID> manager = activeManager(p, active);
        List<UUID> wanted = new ArrayList<>(reportIds);
        wanted.add(p.getUserId());
        manager.ifPresent(wanted::add);
        Map<UUID, AppUser> people = users(wanted);
        AppUser u = people.get(p.getUserId());
        Membership m = active.get(p.getUserId());
        boolean self = p.getUserId().equals(ctx.userId());
        boolean shown = self || HrAccess.manages(ctx);
        return new ProfileView(ref(u), u.getEmail(), m == null ? null : m.getRole().name(), m == null ? null : m.getTitle(),
                p.getDepartment(), manager.map(people::get).map(ProfileService::ref).orElse(null), p.getWorkPhone(), shown,
                shown ? p.getEmploymentType() : null, shown ? p.getStartDate() : null,
                shown ? p.getEmergencyName() : null, shown ? p.getEmergencyPhone() : null,
                shown ? p.getEmergencyRelation() : null,
                reportIds.stream().map(people::get).filter(Objects::nonNull).map(ProfileService::ref)
                        .sorted(Comparator.comparing(PersonRef::name, String.CASE_INSENSITIVE_ORDER)).toList(),
                HrAccess.manages(ctx), self);
    }

    private void contact(List<String> changed, EmployeeProfile p, String phone, String name, String emergencyPhone,
            String relation) {
        change(changed, "workPhone", p.getWorkPhone(), clean(phone), p::setWorkPhone);
        // The emergency contact is private: the log says it changed, never what it is.
        List<String> emergency = new ArrayList<>();
        change(emergency, "x", p.getEmergencyName(), clean(name), p::setEmergencyName);
        change(emergency, "x", p.getEmergencyPhone(), clean(emergencyPhone), p::setEmergencyPhone);
        change(emergency, "x", p.getEmergencyRelation(), clean(relation), p::setEmergencyRelation);
        if (!emergency.isEmpty()) {
            changed.add("emergencyContact");
        }
    }

    private void saved(OrgContext ctx, EmployeeProfile p, List<String> changed) {
        if (changed.isEmpty()) {
            return;
        }
        p.setUpdatedAt(clock.instant());
        AppUser u = users.findById(p.getUserId()).orElseThrow();
        activity.record(ctx.orgId(), HrAccess.MODULE, RECORD, p.getId(), ctx.userId(), "employee.updated",
                Map.of("name", u.getName(), "fields", changed));
    }

    private static <T> void change(List<String> changed, String field, T before, T after, java.util.function.Consumer<T> set) {
        if (!Objects.equals(before, after)) {
            set.accept(after);
            changed.add(field);
        }
    }

    /** Whether {@code person} reports to {@code boss}, directly or not. */
    private boolean reportsTo(UUID orgId, UUID person, UUID boss) {
        Map<UUID, UUID> up = new HashMap<>();
        profiles.findByOrgId(orgId).forEach(x -> {
            if (x.getManagerId() != null) {
                up.put(x.getUserId(), x.getManagerId());
            }
        });
        Set<UUID> seen = new HashSet<>();
        for (UUID at = up.get(person); at != null && seen.add(at); at = up.get(at)) {
            if (at.equals(boss)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<UUID> activeManager(EmployeeProfile p, Map<UUID, Membership> active) {
        return Optional.ofNullable(p.getManagerId()).filter(active::containsKey);
    }

    private Map<UUID, Membership> activeMembers(UUID orgId) {
        return memberships.findByOrgIdOrderByCreatedAt(orgId).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST)
                .collect(Collectors.toMap(Membership::getUserId, Function.identity()));
    }

    private Map<UUID, AppUser> users(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(wanted).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    static PersonRef ref(AppUser u) {
        return u == null ? null : new PersonRef(u.getId(), u.getName(), u.getAvatarUrl());
    }

    static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
