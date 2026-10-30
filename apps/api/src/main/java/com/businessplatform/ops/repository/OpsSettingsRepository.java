package com.businessplatform.ops.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.ops.domain.OpsSettings;

public interface OpsSettingsRepository extends JpaRepository<OpsSettings, UUID> {

    /** Creates the organisation's row with the defaults unless it exists (safe when two first uses race). */
    @Modifying
    @Query(value = "INSERT INTO svc_settings (org_id) VALUES (:orgId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensure(UUID orgId);

    /**
     * The next number of {@code kind} (TICKET or WORK_ORDER): one atomic upsert, so concurrent callers queue on the
     * row and never get the same number; a rolled-back transaction gives its number back.
     */
    @Query(value = "INSERT INTO svc_sequence (org_id, kind, last_value) VALUES (:orgId, :kind, 1)"
            + " ON CONFLICT (org_id, kind) DO UPDATE SET last_value = svc_sequence.last_value + 1"
            + " RETURNING last_value", nativeQuery = true)
    int next(UUID orgId, String kind);

    /** Creates the default SLA policies unless the organisation has them. */
    @Modifying
    @Query(value = "INSERT INTO svc_sla_policy (org_id, priority, response_minutes, resolution_minutes) VALUES"
            + " (:orgId, 'URGENT', 60, 240), (:orgId, 'HIGH', 240, 1440), (:orgId, 'MEDIUM', 480, 2880),"
            + " (:orgId, 'LOW', 1440, 7200) ON CONFLICT DO NOTHING", nativeQuery = true)
    void ensureSla(UUID orgId);
}
