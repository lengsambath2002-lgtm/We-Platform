package com.businessplatform.hr.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.hr.domain.Candidate;
import com.businessplatform.hr.domain.Interview;
import com.businessplatform.hr.domain.InterviewPanel;
import com.businessplatform.hr.domain.JobOpening;
import com.businessplatform.hr.domain.OnboardingItem;
import com.businessplatform.hr.domain.OnboardingTemplate;

/** Recruitment's repositories. */
public final class RecruitmentRepositories {

    private RecruitmentRepositories() {
    }

    public interface JobOpeningRepository extends JpaRepository<JobOpening, UUID> {

        List<JobOpening> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

        Optional<JobOpening> findByIdAndOrgId(UUID id, UUID orgId);

        boolean existsByOrgIdAndHiringManagerIdAndStatusNot(UUID orgId, UUID hiringManagerId, String status);
    }

    public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

        List<Candidate> findByOrgIdAndJobIdOrderByCreatedAtDesc(UUID orgId, UUID jobId);

        List<Candidate> findByOrgIdAndJobIdIn(UUID orgId, Collection<UUID> jobIds);

        Optional<Candidate> findByIdAndOrgId(UUID id, UUID orgId);

        List<Candidate> findByOrgIdAndStageOrderByHiredAtDesc(UUID orgId, String stage);
    }

    public interface InterviewRepository extends JpaRepository<Interview, UUID> {

        List<Interview> findByCandidateIdOrderByStartsAt(UUID candidateId);

        Optional<Interview> findByIdAndOrgId(UUID id, UUID orgId);

        /** Interviews {@code userId} sits on, from {@code since}. */
        @Query("SELECT i FROM Interview i WHERE i.orgId = :orgId AND i.startsAt >= :since"
                + " AND EXISTS (SELECT 1 FROM InterviewPanel p WHERE p.interviewId = i.id AND p.userId = :userId) ORDER BY i.startsAt")
        List<Interview> forPanelist(UUID orgId, UUID userId, Instant since);

        @Query("SELECT i FROM Interview i WHERE i.orgId = :orgId AND i.startsAt >= :from AND i.startsAt < :to ORDER BY i.startsAt")
        List<Interview> between(UUID orgId, Instant from, Instant to);
    }

    public interface InterviewPanelRepository extends JpaRepository<InterviewPanel, InterviewPanel.Key> {

        List<InterviewPanel> findByInterviewIdIn(Collection<UUID> interviewIds);

        boolean existsByUserIdAndInterviewIdIn(UUID userId, Collection<UUID> interviewIds);

        /** Whether {@code userId} is on any interview of the organisation (for the recruitment permission). */
        @Query("SELECT COUNT(p) > 0 FROM InterviewPanel p, Interview i WHERE p.interviewId = i.id AND i.orgId = :orgId AND p.userId = :userId")
        boolean onAnyPanel(UUID orgId, UUID userId);
    }

    public interface OnboardingTemplateRepository extends JpaRepository<OnboardingTemplate, UUID> {

        List<OnboardingTemplate> findByOrgIdOrderBySortOrder(UUID orgId);

        long countByOrgId(UUID orgId);
    }

    public interface OnboardingItemRepository extends JpaRepository<OnboardingItem, UUID> {

        List<OnboardingItem> findByCandidateIdOrderBySortOrder(UUID candidateId);

        List<OnboardingItem> findByCandidateIdIn(Collection<UUID> candidateIds);

        Optional<OnboardingItem> findByIdAndOrgId(UUID id, UUID orgId);
    }
}
