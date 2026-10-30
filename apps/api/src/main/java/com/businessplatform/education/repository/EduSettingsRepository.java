package com.businessplatform.education.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.education.domain.*;

import jakarta.persistence.LockModeType;

public interface EduSettingsRepository extends JpaRepository<EduSettings, UUID> {

    /** Creates the organisation's row with the defaults unless it exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO edu_settings (org_id) VALUES (:orgId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(UUID orgId);

    /** The next number of {@code kind} (STUDENT or INVOICE): one atomic upsert, so numbers never repeat. */
    @Query(value = "INSERT INTO edu_sequence (org_id, kind, last_value) VALUES (:orgId, :kind, 1)"
            + " ON CONFLICT (org_id, kind) DO UPDATE SET last_value = edu_sequence.last_value + 1"
            + " RETURNING last_value", nativeQuery = true)
    int next(UUID orgId, String kind);
}
