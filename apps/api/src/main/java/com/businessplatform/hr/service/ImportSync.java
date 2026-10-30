package com.businessplatform.hr.service;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.org.service.PeopleImported;

import lombok.RequiredArgsConstructor;

/** Applies a bulk import's HR details: manager, 2nd approver, employment type and start date. */
@Component
@RequiredArgsConstructor
public class ImportSync {

    private final EmployeeProfileRepository profiles;
    private final Clock clock;

    @EventListener
    @Transactional
    public void on(PeopleImported ev) {
        profiles.createMissing(ev.orgId());
        Map<UUID, EmployeeProfile> byUser = new HashMap<>();
        profiles.findByOrgId(ev.orgId()).forEach(p -> byUser.put(p.getUserId(), p));
        for (PeopleImported.Person x : ev.people()) {
            EmployeeProfile p = byUser.get(x.userId());
            if (p == null) {
                continue;
            }
            if (x.managerId() != null && byUser.containsKey(x.managerId())) {
                p.setManagerId(x.managerId());
            }
            if (x.secondManagerId() != null && byUser.containsKey(x.secondManagerId())) {
                p.setSecondManagerId(x.secondManagerId());
            }
            if (x.employmentType() != null) {
                p.setEmploymentType(x.employmentType());
            }
            if (x.startDate() != null) {
                p.setStartDate(x.startDate());
            }
            p.setUpdatedAt(clock.instant());
        }
        // With the people already there, the new managers must not go round in a circle.
        Map<UUID, UUID> up = new HashMap<>();
        byUser.values().forEach(p -> {
            if (p.getManagerId() != null) {
                up.put(p.getUserId(), p.getManagerId());
            }
        });
        for (UUID start : up.keySet()) {
            Set<UUID> seen = new HashSet<>();
            for (UUID at = start; at != null; at = up.get(at)) {
                if (!seen.add(at)) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Managers go round in a circle", List.of());
                }
            }
        }
    }
}
