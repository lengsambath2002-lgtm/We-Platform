package com.businessplatform.business.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.business.domain.BizSettings;

public interface BizSettingsRepository extends JpaRepository<BizSettings, UUID> {

    /** Creates the organisation's row with the defaults unless it exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO biz_settings (org_id) VALUES (:orgId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(UUID orgId);

    /**
     * The next number of {@code kind} (QUOTE or INVOICE) for the organisation: one atomic upsert, so concurrent
     * callers queue on the row and never get the same number; a rolled-back transaction gives its number back.
     */
    @Query(value = "INSERT INTO biz_sequence (org_id, kind, last_value) VALUES (:orgId, :kind, 1)"
            + " ON CONFLICT (org_id, kind) DO UPDATE SET last_value = biz_sequence.last_value + 1"
            + " RETURNING last_value", nativeQuery = true)
    int next(UUID orgId, String kind);
}
