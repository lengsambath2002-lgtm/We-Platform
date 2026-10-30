package com.businessplatform.ops.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.ops.domain.Staff;
import com.businessplatform.ops.payload.OpsPayloads.PersonRef;
import com.businessplatform.ops.payload.OpsPayloads.StaffRequest;
import com.businessplatform.ops.payload.OpsPayloads.StaffView;
import com.businessplatform.ops.repository.StaffRepository;
import com.businessplatform.ops.repository.WoTechnicianRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;

import lombok.RequiredArgsConstructor;

/**
 * The service staff: members with a role in the module (dispatcher or technician, with skills and a phone).
 * Admins add, change and switch them off; dispatchers see the list (to assign work); technicians don't.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    public static final String RECORD = "service_staff";

    private final OpsAccess access;
    private final StaffRepository staff;
    private final MembershipRepository memberships;
    private final UserRepository users;
    private final WoTechnicianRepository woTechnicians;
    private final ActivityService activity;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<StaffView> list(String slug) {
        OpsAccess.Who w = access.requireManager(slug);
        List<Staff> all = staff.findByOrgIdOrderByCreatedAt(w.orgId());
        Map<UUID, AppUser> us = new HashMap<>();
        users.findByIdIn(all.stream().map(Staff::getUserId).toList()).forEach(u -> us.put(u.getId(), u));
        Map<UUID, Membership> ms = new HashMap<>();
        memberships.findByOrgIdOrderByCreatedAt(w.orgId()).forEach(m -> ms.put(m.getUserId(), m));
        Map<UUID, Long> open = new HashMap<>();
        woTechnicians.openPerTechnician(w.orgId()).forEach(r -> open.put((UUID) r[0], ((Number) r[1]).longValue()));
        return all.stream().filter(s -> us.containsKey(s.getUserId())).map(s -> {
            AppUser u = us.get(s.getUserId());
            Membership m = ms.get(s.getUserId());
            boolean member = m != null && Membership.ACTIVE.equals(m.getStatus());
            return new StaffView(s.getId(), new PersonRef(u.getId(), u.getName(), u.getAvatarUrl()), u.getEmail(),
                    m == null ? null : m.getRole().name(), s.getRole(), s.getSkills(), s.getPhone(), s.isActive() && member,
                    open.getOrDefault(s.getUserId(), 0L));
        }).sorted((a, b) -> a.person().name().compareToIgnoreCase(b.person().name())).toList();
    }

    @Transactional
    public StaffView create(String slug, StaffRequest in) {
        OpsAccess.Who w = access.requireAdmin(slug);
        if (in.userId() == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Choose a member");
        }
        Membership m = memberships.findByOrgIdAndUserId(w.orgId(), in.userId())
                .filter(x -> Membership.ACTIVE.equals(x.getStatus()) && !x.getRole().equals(com.businessplatform.org.domain.OrgRole.GUEST))
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "userId", "Choose a member"));
        if (staff.findByOrgIdAndUserId(w.orgId(), m.getUserId()).isPresent()) {
            throw BusinessException.field(ErrorCode.CONFLICT, "userId", "This person is already on the service staff");
        }
        Staff s = new Staff();
        s.setId(Ids.next());
        s.setOrgId(w.orgId());
        s.setUserId(m.getUserId());
        s.setCreatedAt(clock.instant());
        apply(s, in);
        staff.saveAndFlush(s);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, s.getId(), w.userId(), "service.staff.added",
                Map.of("name", name(s.getUserId()), "role", s.getRole()));
        return find(w, s.getId());
    }

    @Transactional
    public StaffView update(String slug, UUID id, StaffRequest in) {
        OpsAccess.Who w = access.requireAdmin(slug);
        Staff s = staff.findByIdAndOrgId(id, w.orgId()).orElseThrow(BusinessException::notFound);
        String before = s.getRole();
        boolean wasActive = s.isActive();
        apply(s, in);
        activity.record(w.orgId(), OpsAccess.MODULE, RECORD, s.getId(), w.userId(), "service.staff.updated",
                Map.of("name", name(s.getUserId()), "role", s.getRole(), "from", before, "active", s.isActive(),
                        "wasActive", wasActive));
        return find(w, s.getId());
    }

    private StaffView find(OpsAccess.Who w, UUID id) {
        return list(w.ctx().slug()).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    private void apply(Staff s, StaffRequest in) {
        s.setRole(in.role());
        s.setSkills(in.skills() == null ? List.of() : in.skills().stream().map(String::strip).distinct().toList());
        s.setPhone(OpsPeople.clean(in.phone()));
        s.setActive(in.active() == null || in.active());
        s.setUpdatedAt(clock.instant());
    }

    private String name(UUID userId) {
        return users.findById(userId).map(AppUser::getName).orElse("");
    }
}
