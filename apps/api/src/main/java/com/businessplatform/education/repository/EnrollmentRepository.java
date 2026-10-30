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

public interface EnrollmentRepository extends JpaRepository<Enrollment, Enrollment.Key> {

    List<Enrollment> findByClassId(UUID classId);

    List<Enrollment> findByClassIdIn(Collection<UUID> classIds);

    List<Enrollment> findByStudentId(UUID studentId);

    List<Enrollment> findByStudentIdIn(Collection<UUID> studentIds);

    Optional<Enrollment> findByStudentIdAndYearId(UUID studentId, UUID yearId);

    Optional<Enrollment> findByClassIdAndStudentId(UUID classId, UUID studentId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.classId = :classId AND e.leftOn IS NULL")
    long countCurrent(UUID classId);
}
