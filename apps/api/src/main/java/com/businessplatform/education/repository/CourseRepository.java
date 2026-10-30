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

public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByClassId(UUID classId);

    List<Course> findByClassIdIn(Collection<UUID> classIds);

    List<Course> findByTeacherId(UUID teacherId);

    List<Course> findByOrgId(UUID orgId);

    Optional<Course> findByIdAndOrgId(UUID id, UUID orgId);

    boolean existsByClassIdAndSubjectId(UUID classId, UUID subjectId);

    boolean existsBySubjectId(UUID subjectId);
}
