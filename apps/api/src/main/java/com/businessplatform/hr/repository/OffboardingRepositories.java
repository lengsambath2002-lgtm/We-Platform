package com.businessplatform.hr.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.Offboarding;
import com.businessplatform.hr.domain.OffboardingItem;
import com.businessplatform.hr.domain.OffboardingTemplate;

/** Offboardings, their checklists and the organisation's checklist template. */
public final class OffboardingRepositories {

    private OffboardingRepositories() {
    }

    public interface Offboardings extends JpaRepository<Offboarding, UUID> {

        List<Offboarding> findByOrgIdOrderByLastDayDesc(UUID orgId);

        List<Offboarding> findByOrgIdAndUserIdOrderByCreatedAtDesc(UUID orgId, UUID userId);

        Optional<Offboarding> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface Items extends JpaRepository<OffboardingItem, UUID> {

        List<OffboardingItem> findByOffboardingIdOrderBySortOrderAscCreatedAtAsc(UUID offboardingId);

        Optional<OffboardingItem> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface Templates extends JpaRepository<OffboardingTemplate, UUID> {

        List<OffboardingTemplate> findByOrgIdOrderBySortOrder(UUID orgId);
    }
}
