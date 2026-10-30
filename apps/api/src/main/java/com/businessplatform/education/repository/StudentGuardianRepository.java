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

public interface StudentGuardianRepository extends JpaRepository<StudentGuardian, StudentGuardian.Key> {

    List<StudentGuardian> findByStudentId(UUID studentId);

    List<StudentGuardian> findByStudentIdIn(Collection<UUID> studentIds);

    List<StudentGuardian> findByGuardianId(UUID guardianId);

    List<StudentGuardian> findByGuardianIdIn(Collection<UUID> guardianIds);

    Optional<StudentGuardian> findByStudentIdAndGuardianId(UUID studentId, UUID guardianId);

    @Modifying
    @Query("UPDATE StudentGuardian g SET g.primary = false WHERE g.studentId = :studentId")
    void clearPrimary(UUID studentId);
}
