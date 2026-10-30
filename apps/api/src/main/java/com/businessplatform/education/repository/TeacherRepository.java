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

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {

    List<Teacher> findByOrgId(UUID orgId);

    Optional<Teacher> findByIdAndOrgId(UUID id, UUID orgId);

    Optional<Teacher> findByOrgIdAndUserId(UUID orgId, UUID userId);

    /** The teacher, locked until the transaction ends (timetable changes for one teacher one at a time). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Teacher t WHERE t.id = :id AND t.orgId = :orgId")
    Optional<Teacher> lock(UUID id, UUID orgId);
}
