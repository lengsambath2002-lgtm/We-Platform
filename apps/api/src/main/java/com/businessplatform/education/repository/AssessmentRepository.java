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

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByCourseIdOrderByHeldOnAscNameAsc(UUID courseId);

    List<Assessment> findByCourseIdAndTermIdOrderByHeldOnAscNameAsc(UUID courseId, UUID termId);

    List<Assessment> findByCourseIdInAndTermId(Collection<UUID> courseIds, UUID termId);

    List<Assessment> findByCourseIdInOrderByHeldOnDesc(Collection<UUID> courseIds);

    List<Assessment> findByOrgIdOrderByHeldOnDesc(UUID orgId);

    List<Assessment> findByOrgIdAndHeldOnBetweenOrderByHeldOn(UUID orgId, LocalDate from, LocalDate to);

    Optional<Assessment> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByTermId(UUID termId);
}
