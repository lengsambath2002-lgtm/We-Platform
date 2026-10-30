package com.businessplatform.hr.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** HR & Workforce: performance reviews and training. */
public final class DevelopmentPayloads {

    private DevelopmentPayloads() {
    }

    // ── Reviews ──────────────────────────────────────────────────────────────────────

    public record CycleRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd, @NotNull LocalDate dueDate,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 300) String> questions) {
    }

    /** {@code rating} may be empty while saving a draft; submitting needs every rating. */
    public record Answer(@Min(1) @Max(5) Integer rating, @Size(max = 2000) String comment) {
    }

    /** {@code overallRating}: the reviewer's only. {@code submit}: hand it on (else it is saved as a draft). */
    public record AnswersRequest(@NotNull @Size(max = 20) List<@Valid @NotNull Answer> answers,
            @Size(max = 5000) String summary, @Min(1) @Max(5) Integer overallRating, boolean submit) {
    }

    public record ReviewerRequest(@NotNull UUID reviewerId) {
    }

    /** {@code total}: reviews in the cycle; the others count how far they have got. */
    public record CycleView(UUID id, String name, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate,
            List<String> questions, String status, int total, int selfDone, int reviewed, int acknowledged,
            Instant launchedAt, Instant closedAt) {
    }

    public record ReviewRow(UUID id, UUID cycleId, String cycleName, LocalDate dueDate, String cycleStatus,
            PersonRef employee, PersonRef reviewer, String status, Integer overallRating) {
    }

    /**
     * One review as the caller may see it: the self-assessment for the person, the reviewer and HR; the reviewer's
     * part for the reviewer and HR, and for the person once it is shared (DONE).
     */
    public record ReviewView(UUID id, CycleView cycle, PersonRef employee, PersonRef reviewer, String status,
            List<Answer> selfAnswers, String selfSummary, Instant selfSubmittedAt, List<Answer> managerAnswers,
            String managerSummary, Integer overallRating, Instant managerSubmittedAt, Instant acknowledgedAt,
            boolean canEditSelf, boolean canEditReview, boolean canAcknowledge, boolean canChangeReviewer) {
    }

    public record CycleDetail(CycleView cycle, List<ReviewRow> reviews) {
    }

    // ── Training ─────────────────────────────────────────────────────────────────────

    public record CourseRequest(@NotBlank @Size(max = 160) String title, @Size(max = 5000) String description,
            @Size(max = 120) String provider, @Size(max = 200) String location, LocalDate startsOn, LocalDate endsOn,
            @DecimalMin("0.5") @DecimalMax("9999") BigDecimal hours, @Min(1) @Max(10000) Integer capacity,
            boolean selfEnrol, @Pattern(regexp = "OPEN|CLOSED") String status) {
    }

    public record EnrolRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> userIds) {
    }

    public record EnrolmentStatusRequest(@NotNull @Pattern(regexp = "ENROLLED|COMPLETED|CANCELLED") String status) {
    }

    /** {@code myStatus}: the caller's enrolment, if any; {@code canEnrol}: they may sign up now. */
    public record CourseView(UUID id, String title, String description, String provider, String location,
            LocalDate startsOn, LocalDate endsOn, BigDecimal hours, Integer capacity, boolean selfEnrol, String status,
            int enrolled, int completed, String myStatus, UUID myEnrolmentId, boolean canEnrol) {
    }

    public record EnrolmentView(UUID id, PersonRef person, String status, PersonRef enrolledBy, Instant completedAt,
            Instant createdAt) {
    }

    /** {@code enrolments}: everyone on the course, for HR managers only (empty for others). */
    public record CourseDetail(CourseView course, List<EnrolmentView> enrolments, boolean canManage) {
    }

    public record MyTraining(UUID enrolmentId, CourseView course, String status, Instant completedAt) {
    }
}
