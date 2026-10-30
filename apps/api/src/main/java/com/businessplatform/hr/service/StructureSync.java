package com.businessplatform.hr.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.StructureChanged;
import com.businessplatform.org.service.OrgStructureService;

import lombok.RequiredArgsConstructor;

/**
 * Keeps HR in step with the organisation structure: each profile's department is the one the structure gives
 * (database function {@code org_department_of}), and someone newly placed without a manager gets the one the
 * structure gives (their reports-to position's holder, else their unit's head).
 */
@Component
@RequiredArgsConstructor
public class StructureSync {

    private final EmployeeProfileRepository profiles;
    private final OrgStructureService structure;
    private final ModuleService modules;
    private final java.time.Clock clock;

    @EventListener
    @Transactional
    public void on(StructureChanged ev) {
        if (modules.enabled(ev.orgId()).contains(HrAccess.MODULE)) {
            profiles.createMissing(ev.orgId());
        }
        Map<UUID, String> departments = new HashMap<>();
        profiles.structureDepartments(ev.orgId()).forEach(r -> departments.put(UUID.fromString((String) r[0]), (String) r[1]));
        Map<UUID, UUID> managers = new HashMap<>();
        profiles.findByOrgId(ev.orgId()).forEach(p -> {
            if (p.getManagerId() != null) {
                managers.put(p.getUserId(), p.getManagerId());
            }
        });
        Set<UUID> placed = new HashSet<>(ev.placed());
        for (EmployeeProfile p : profiles.findByOrgId(ev.orgId())) {
            boolean touched = false;
            String dept = departments.get(p.getMembershipId());
            if (!Objects.equals(dept, p.getDepartment())) {
                p.setDepartment(dept);
                touched = true;
            }
            if (placed.contains(p.getUserId()) && p.getManagerId() == null) {
                UUID boss = structure.structuralManager(ev.orgId(), p.getUserId()).filter(b -> !reportsTo(managers, b, p.getUserId())).orElse(null);
                if (boss != null) {
                    p.setManagerId(boss);
                    managers.put(p.getUserId(), boss);
                    touched = true;
                }
            }
            if (touched) {
                p.setUpdatedAt(clock.instant());
            }
        }
    }

    private static boolean reportsTo(Map<UUID, UUID> up, UUID person, UUID boss) {
        Set<UUID> seen = new HashSet<>();
        for (UUID at = person; at != null && seen.add(at); at = up.get(at)) {
            if (at.equals(boss)) {
                return true;
            }
        }
        return false;
    }
}
