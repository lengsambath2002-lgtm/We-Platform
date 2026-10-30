package com.businessplatform.hr.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HR & Workforce: recruitment (openings, candidates, interviews) and onboarding. */
public final class RecruitmentPayloads {

    private RecruitmentPayloads() {
    }

    private static final String PHONE = "^$|^[0-9+()\\-. ]{6,30}$";

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** {@code status} null = OPEN. */
    public record JobRequest(@NotBlank @Size(max = 120) String title, @Size(max = 80) String department,
            @Pattern(regexp = "^$|FULL_TIME|PART_TIME|CONTRACT|INTERN") String employmentType,
            @Size(max = 120) String location, @Size(max = 10000) String description, @Min(1) @Max(500) int openings,
            UUID hiringManagerId, @Pattern(regexp = "OPEN|ON_HOLD|CLOSED") String status) {
    }

    public record CandidateRequest(@NotBlank @Size(max = 120) String name, @Email @Size(max = 254) String email,
            @Pattern(regexp = PHONE, message = "Use digits, spaces and + ( ) - only") String phone,
            @NotNull @Pattern(regexp = "REFERRAL|JOB_BOARD|WEBSITE|WALK_IN|AGENCY|OTHER") String source,
            @Min(1) @Max(5) Integer rating, @Size(max = 10000) String notes) {
    }

    /** Hiring is its own step ({@link HireRequest}); REJECTED needs a reason. */
    public record StageRequest(@NotNull @Pattern(regexp = "NEW|SCREENING|INTERVIEW|OFFER|REJECTED") String stage,
            @Size(max = 500) String reason) {
    }

    public record InterviewRequest(@NotNull Instant startsAt, @Min(10) @Max(480) int durationMinutes,
            @Size(max = 200) String location, @NotEmpty @Size(max = 10) List<@NotNull UUID> panel) {
    }

    public record FeedbackRequest(@NotNull @Pattern(regexp = "STRONG_YES|YES|NO|STRONG_NO") String recommendation,
            @Size(max = 5000) String notes) {
    }

    /** {@code invite}: also invite the candidate's email to the organisation as a member. */
    public record HireRequest(@NotNull LocalDate startDate, boolean invite) {
    }

    public record TemplateItem(@NotBlank @Size(max = 200) String title, @Min(-60) @Max(180) int dueDays) {
    }

    public record TemplateRequest(@NotNull @Size(max = 30) List<@Valid @NotNull TemplateItem> items) {
    }

    public record OnboardingItemRequest(@NotBlank @Size(max = 200) String title, LocalDate dueDate) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    /** {@code stages}: how many candidates are at each stage. */
    public record JobView(UUID id, String title, String department, String employmentType, String location,
            String description, int openings, PersonRef hiringManager, String status, Map<String, Long> stages,
            Instant createdAt, boolean canManage) {
    }

    public record CandidateSummary(UUID id, UUID jobId, String name, String email, String phone, String source,
            String stage, Integer rating, boolean hasCv, Instant nextInterview, Instant createdAt) {
    }

    /** {@code notes} of a panelist: theirs, or everyone's once they have given their own (HR and the hiring manager always). */
    public record PanelView(PersonRef person, String recommendation, String notes, Instant submittedAt) {
    }

    public record InterviewView(UUID id, UUID candidateId, String candidateName, String jobTitle, Instant startsAt,
            int durationMinutes, String location, List<PanelView> panel, boolean canGiveFeedback) {
    }

    public record OnboardingView(UUID id, String title, LocalDate dueDate, boolean done, Instant doneAt, PersonRef doneBy) {
    }

    /** {@code joined}: the invitation was accepted. */
    public record CandidateDetail(CandidateSummary candidate, JobView job, String notes, String rejectedReason,
            LocalDate startDate, String cvName, Long cvSize, boolean invited, boolean joined,
            List<InterviewView> interviews, List<OnboardingView> onboarding, boolean canManage, boolean canHire) {
    }

    public record HireView(CandidateSummary candidate, String jobTitle, LocalDate startDate, int done, int total,
            boolean joined) {
    }

    public record TemplateView(List<TemplateItem> items) {
    }
}
