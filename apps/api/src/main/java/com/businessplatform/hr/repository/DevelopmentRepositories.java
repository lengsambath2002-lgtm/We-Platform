package com.businessplatform.hr.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.businessplatform.hr.domain.Review;
import com.businessplatform.hr.domain.ReviewCycle;
import com.businessplatform.hr.domain.TrainingCourse;
import com.businessplatform.hr.domain.TrainingEnrolment;

/** Repositories of performance reviews and training. */
public final class DevelopmentRepositories {

    private DevelopmentRepositories() {
    }

    public interface ReviewCycleRepository extends JpaRepository<ReviewCycle, UUID> {

        List<ReviewCycle> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

        Optional<ReviewCycle> findByIdAndOrgId(UUID id, UUID orgId);

        boolean existsByOrgIdAndNameIgnoreCase(UUID orgId, String name);
    }

    public interface ReviewRepository extends JpaRepository<Review, UUID> {

        List<Review> findByCycleId(UUID cycleId);

        List<Review> findByCycleIdIn(Collection<UUID> cycleIds);

        Optional<Review> findByIdAndOrgId(UUID id, UUID orgId);

        List<Review> findByOrgIdAndEmployeeId(UUID orgId, UUID employeeId);

        List<Review> findByOrgIdAndReviewerId(UUID orgId, UUID reviewerId);
    }

    public interface TrainingCourseRepository extends JpaRepository<TrainingCourse, UUID> {

        List<TrainingCourse> findByOrgIdOrderByStartsOnDescCreatedAtDesc(UUID orgId);

        Optional<TrainingCourse> findByIdAndOrgId(UUID id, UUID orgId);
    }

    public interface TrainingEnrolmentRepository extends JpaRepository<TrainingEnrolment, UUID> {

        List<TrainingEnrolment> findByCourseId(UUID courseId);

        List<TrainingEnrolment> findByCourseIdIn(Collection<UUID> courseIds);

        List<TrainingEnrolment> findByOrgIdAndUserId(UUID orgId, UUID userId);

        Optional<TrainingEnrolment> findByCourseIdAndUserId(UUID courseId, UUID userId);

        Optional<TrainingEnrolment> findByIdAndOrgId(UUID id, UUID orgId);
    }
}
