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

public interface ScoreRepository extends JpaRepository<Score, Score.Key> {

    List<Score> findByAssessmentId(UUID assessmentId);

    List<Score> findByAssessmentIdIn(Collection<UUID> assessmentIds);

    List<Score> findByStudentIdAndAssessmentIdIn(UUID studentId, Collection<UUID> assessmentIds);

    long countByAssessmentId(UUID assessmentId);
}
