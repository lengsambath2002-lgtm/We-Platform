package com.businessplatform.module.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.module.domain.OrgModule;

public interface OrgModuleRepository extends JpaRepository<OrgModule, OrgModule.Key> {

    List<OrgModule> findByOrgId(UUID orgId);
}
