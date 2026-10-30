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

public interface PeriodRepository extends JpaRepository<Period, UUID> {

    List<Period> findByClassId(UUID classId);

    List<Period> findByCourseIdIn(Collection<UUID> courseIds);

    Optional<Period> findByIdAndOrgId(UUID id, UUID orgId);

    /** Lessons on {@code weekday} in the classes of one academic year (to find double bookings). */
    @Query("SELECT p FROM Period p WHERE p.orgId = :orgId AND p.weekday = :weekday AND p.classId IN"
            + " (SELECT c.id FROM SchoolClass c WHERE c.yearId = :yearId)")
    List<Period> onDay(UUID orgId, UUID yearId, short weekday);
}
