package com.businessplatform.platform.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.platform.domain.PlatformAudit;

public interface PlatformAuditRepository extends JpaRepository<PlatformAudit, UUID> {
}
