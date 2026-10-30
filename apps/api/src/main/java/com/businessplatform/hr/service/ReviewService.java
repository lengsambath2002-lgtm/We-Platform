package com.businessplatform.hr.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.hr.domain.EmployeeProfile;
import com.businessplatform.hr.domain.Review;
import com.businessplatform.hr.domain.ReviewCycle;
import com.businessplatform.hr.payload.DevelopmentPayloads.*;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewCycleRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.ReviewRepository;
import com.businessplatform.hr.repository.EmployeeProfileRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Performance reviews. HR managers write a cycle's questions and launch it: everyone active gets a review, with
 * their manager as reviewer (or the HR manager who launched it). The person assesses themselves; the reviewer then
 * sees that, rates each question and gives an overall rating; the result is shared with the person, who
 * acknowledges it. Nothing changes once the cycle is closed.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** How cycles and reviews appear in the activity log. */
    public static final String CYCLE = "review_cycle";
    public static final String REVIEW = "review";

    private final HrAccess hr;
    private final ProfileService profiles;
    private final ReviewCycleRepository cycles;
    private final ReviewRepository reviews;
    private final EmployeeProfileRepository profileRows;
    private final MembershipRepository memberships;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Cycles (HR managers) ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CycleView> cycles(String slug) {
        OrgContext ctx = hr.requireManager(slug);
        List<ReviewCycle> found = cycles.findByOrgIdOrderByCreatedAtDesc(ctx.orgId());
        Map<UUID, List<Review>> byCycle = reviews.findByCycleIdIn(found.stream().map(ReviewCycle::getId).toList()).stream()
                .collect(Collectors.groupingBy(Review::getCycleId));
        return found.stream().map(c -> view(c, byCycle.getOrDefault(c.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public CycleDetail cycle(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        ReviewCycle c = cycle(ctx, id);
        List<Review> found = reviews.findByCycleId(c.getId());
        return new CycleDetail(view(c, found), rows(found, Map.of(c.getId(), c)));
    }

    @Transactional
    public CycleView saveCycle(String slug, UUID id, CycleRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        ReviewCycle c = id == null ? new ReviewCycle() : cycle(ctx, id);
        if (id != null && !ReviewCycle.DRAFT.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A launched cycle can't be changed", List.of());
        }
        String name = in.name().strip();
        if ((id == null || !c.getName().equalsIgnoreCase(name)) && cycles.existsByOrgIdAndNameIgnoreCase(ctx.orgId(), name)) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another review cycle has this name");
        }
        if (in.periodEnd().isBefore(in.periodStart())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "periodEnd", "The period ends before it starts");
        }
        if (id == null) {
            c.setId(Ids.next());
            c.setOrgId(ctx.orgId());
            c.setStatus(ReviewCycle.DRAFT);
            c.setCreatedBy(ctx.userId());
            c.setCreatedAt(clock.instant());
        }
        c.setName(name);
        c.setPeriodStart(in.periodStart());
        c.setPeriodEnd(in.periodEnd());
        c.setDueDate(in.dueDate());
        c.setQuestions(in.questions().stream().map(q -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", q.strip());
            return m;
        }).toList());
        c.setUpdatedAt(clock.instant());
        cycles.save(c);
        activity.record(ctx.orgId(), HrAccess.MODULE, CYCLE, c.getId(), ctx.userId(), id == null ? "review.cycle.created" : "review.cycle.updated",
                Map.of("name", c.getName()));
        return view(c, List.of());
    }

    /** Opens a DRAFT: a review for every active member (not guests), reviewed by their manager or by the caller. */
    @Transactional
    public CycleDetail launch(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        ReviewCycle c = cycle(ctx, id);
        if (!ReviewCycle.DRAFT.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This cycle is launched already", List.of());
        }
        profileRows.createMissing(ctx.orgId());
        Set<UUID> active = activeMembers(ctx);
        Map<UUID, EmployeeProfile> byUser = profileRows.findByOrgId(ctx.orgId()).stream()
                .collect(Collectors.toMap(EmployeeProfile::getUserId, Function.identity()));
        List<Review> created = new ArrayList<>();
        for (UUID person : active) {
            EmployeeProfile p = byUser.get(person);
            UUID manager = p == null ? null : p.getManagerId();
            Review r = new Review();
            r.setId(Ids.next());
            r.setOrgId(ctx.orgId());
            r.setCycleId(c.getId());
            r.setEmployeeId(person);
            r.setReviewerId(manager != null && active.contains(manager) ? manager : ctx.userId());
            r.setStatus(Review.SELF);
            r.setCreatedAt(clock.instant());
            r.setUpdatedAt(r.getCreatedAt());
            reviews.save(r);
            created.add(r);
        }
        c.setStatus(ReviewCycle.OPEN);
        c.setLaunchedAt(clock.instant());
        c.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, CYCLE, c.getId(), ctx.userId(), "review.cycle.launched",
                Map.of("name", c.getName(), "reviews", created.size()));
        created.forEach(r -> notifications.send(ctx.orgId(), List.of(r.getEmployeeId()), ctx.userId(), "review.self",
                Text.of("Your review for {cycle}: please assess yourself", Map.of("cycle", c.getName())), null, link(ctx, r)));
        return new CycleDetail(view(c, created), rows(created, Map.of(c.getId(), c)));
    }

    @Transactional
    public CycleView close(String slug, UUID id) {
        OrgContext ctx = hr.requireManager(slug);
        ReviewCycle c = cycle(ctx, id);
        if (!ReviewCycle.OPEN.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an open cycle can be closed", List.of());
        }
        c.setStatus(ReviewCycle.CLOSED);
        c.setClosedAt(clock.instant());
        c.setUpdatedAt(clock.instant());
        activity.record(ctx.orgId(), HrAccess.MODULE, CYCLE, c.getId(), ctx.userId(), "review.cycle.closed", Map.of("name", c.getName()));
        return view(c, reviews.findByCycleId(c.getId()));
    }

    /** HR managers: hands a review not yet shared to another reviewer. */
    @Transactional
    public ReviewView changeReviewer(String slug, UUID id, ReviewerRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        Review r = review(ctx, id);
        ReviewCycle c = openCycle(ctx, r);
        if (!activeMembers(ctx).contains(in.reviewerId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "reviewerId", "Choose a member of the organisation");
        }
        if (Review.DONE.equals(r.getStatus()) || Review.ACKNOWLEDGED.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This review is finished", List.of());
        }
        r.setReviewerId(in.reviewerId());
        r.setManagerAnswers(null);
        r.setManagerSummary(null);
        r.setOverallRating(null);
        r.setUpdatedAt(clock.instant());
        return view(ctx, c, r);
    }

    // ── Reviews ──────────────────────────────────────────────────────────────────────

    /** Reviews about me, newest first. */
    @Transactional(readOnly = true)
    public List<ReviewRow> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        return rowsWithCycles(ctx, reviews.findByOrgIdAndEmployeeId(ctx.orgId(), ctx.userId()), true);
    }

    /** Reviews I write. */
    @Transactional(readOnly = true)
    public List<ReviewRow> toReview(String slug) {
        OrgContext ctx = hr.require(slug);
        return rowsWithCycles(ctx, reviews.findByOrgIdAndReviewerId(ctx.orgId(), ctx.userId()).stream()
                .filter(r -> !r.getEmployeeId().equals(ctx.userId())).toList(), false);
    }

    @Transactional(readOnly = true)
    public ReviewView get(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Review r = review(ctx, id);
        if (!r.getEmployeeId().equals(ctx.userId()) && !r.getReviewerId().equals(ctx.userId()) && !HrAccess.manages(ctx)) {
            throw BusinessException.notFound();
        }
        return view(ctx, cycles.findById(r.getCycleId()).orElseThrow(), r);
    }

    /** The person's self-assessment, while it is theirs to give. */
    @Transactional
    public ReviewView saveSelf(String slug, UUID id, AnswersRequest in) {
        OrgContext ctx = hr.require(slug);
        Review r = review(ctx, id);
        if (!r.getEmployeeId().equals(ctx.userId())) {
            throw BusinessException.notFound();
        }
        ReviewCycle c = openCycle(ctx, r);
        if (!Review.SELF.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Your self-assessment is sent already", List.of());
        }
        checkAnswers(c, in);
        r.setSelfAnswers(answers(in.answers()));
        r.setSelfSummary(ProfileService.clean(in.summary()));
        r.setUpdatedAt(clock.instant());
        if (in.submit()) {
            r.setSelfSubmittedAt(clock.instant());
            r.setStatus(Review.MANAGER);
            activity.record(ctx.orgId(), HrAccess.MODULE, REVIEW, r.getId(), ctx.userId(), "review.self.submitted", Map.of("cycle", c.getName()));
            notifications.send(ctx.orgId(), List.of(r.getReviewerId()), ctx.userId(), "review.manager",
                    Text.of("A review is ready for you: {cycle}", Map.of("cycle", c.getName())), null, link(ctx, r));
        }
        return view(ctx, c, r);
    }

    /** The reviewer's assessment, once the self-assessment is in. */
    @Transactional
    public ReviewView saveReview(String slug, UUID id, AnswersRequest in) {
        OrgContext ctx = hr.require(slug);
        Review r = review(ctx, id);
        if (!r.getReviewerId().equals(ctx.userId())) {
            throw r.getEmployeeId().equals(ctx.userId()) || HrAccess.manages(ctx) ? BusinessException.forbidden() : BusinessException.notFound();
        }
        ReviewCycle c = openCycle(ctx, r);
        if (!Review.MANAGER.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, Review.SELF.equals(r.getStatus())
                    ? "Wait for the self-assessment first" : "This review is shared already", List.of());
        }
        checkAnswers(c, in);
        if (in.submit() && in.overallRating() == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "overallRating", "Give an overall rating");
        }
        r.setManagerAnswers(answers(in.answers()));
        r.setManagerSummary(ProfileService.clean(in.summary()));
        r.setOverallRating(in.overallRating() == null ? null : in.overallRating().shortValue());
        r.setUpdatedAt(clock.instant());
        if (in.submit()) {
            r.setManagerSubmittedAt(clock.instant());
            r.setStatus(Review.DONE);
            activity.record(ctx.orgId(), HrAccess.MODULE, REVIEW, r.getId(), ctx.userId(), "review.shared", Map.of("cycle", c.getName()));
            notifications.send(ctx.orgId(), List.of(r.getEmployeeId()), ctx.userId(), "review.done",
                    Text.of("Your review for {cycle} is ready to read", Map.of("cycle", c.getName())), null, link(ctx, r));
        }
        return view(ctx, c, r);
    }

    @Transactional
    public ReviewView acknowledge(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        Review r = review(ctx, id);
        if (!r.getEmployeeId().equals(ctx.userId())) {
            throw BusinessException.notFound();
        }
        if (!Review.DONE.equals(r.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "There is nothing to acknowledge yet", List.of());
        }
        r.setStatus(Review.ACKNOWLEDGED);
        r.setAcknowledgedAt(clock.instant());
        r.setUpdatedAt(clock.instant());
        ReviewCycle c = cycles.findById(r.getCycleId()).orElseThrow();
        activity.record(ctx.orgId(), HrAccess.MODULE, REVIEW, r.getId(), ctx.userId(), "review.acknowledged", Map.of("cycle", c.getName()));
        return view(ctx, c, r);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private ReviewCycle cycle(OrgContext ctx, UUID id) {
        return cycles.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Review review(OrgContext ctx, UUID id) {
        return reviews.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private ReviewCycle openCycle(OrgContext ctx, Review r) {
        ReviewCycle c = cycles.findById(r.getCycleId()).orElseThrow();
        if (!ReviewCycle.OPEN.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This review cycle is closed", List.of());
        }
        return c;
    }

    private Set<UUID> activeMembers(OrgContext ctx) {
        return memberships.findByOrgIdOrderByCreatedAt(ctx.orgId()).stream()
                .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST)
                .map(Membership::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** One answer per question; submitting needs every rating. */
    private static void checkAnswers(ReviewCycle c, AnswersRequest in) {
        if (in.answers().size() != c.getQuestions().size()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "answers", "Answer each question");
        }
        if (in.submit() && in.answers().stream().anyMatch(a -> a.rating() == null)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "answers", "Rate every question before sending");
        }
    }

    private static List<Map<String, Object>> answers(List<Answer> in) {
        return in.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rating", a.rating());
            m.put("comment", ProfileService.clean(a.comment()));
            return m;
        }).toList();
    }

    private static List<Answer> answers(List<Map<String, Object>> stored, boolean show) {
        if (!show || stored == null) {
            return null;
        }
        return stored.stream().map(m -> new Answer(m.get("rating") == null ? null : ((Number) m.get("rating")).intValue(),
                (String) m.get("comment"))).toList();
    }

    private static String link(OrgContext ctx, Review r) {
        return "/" + ctx.slug() + "/hr/reviews/" + r.getId();
    }

    private static CycleView view(ReviewCycle c, List<Review> found) {
        int self = 0;
        int reviewed = 0;
        int ack = 0;
        for (Review r : found) {
            if (r.getSelfSubmittedAt() != null) {
                self++;
            }
            if (r.getManagerSubmittedAt() != null) {
                reviewed++;
            }
            if (r.getAcknowledgedAt() != null) {
                ack++;
            }
        }
        return new CycleView(c.getId(), c.getName(), c.getPeriodStart(), c.getPeriodEnd(), c.getDueDate(),
                c.getQuestions().stream().map(q -> String.valueOf(q.get("text"))).toList(), c.getStatus(), found.size(),
                self, reviewed, ack, c.getLaunchedAt(), c.getClosedAt());
    }

    private ReviewView view(OrgContext ctx, ReviewCycle c, Review r) {
        Map<UUID, PersonRef> people = profiles.persons(List.of(r.getEmployeeId(), r.getReviewerId()));
        boolean employee = r.getEmployeeId().equals(ctx.userId());
        boolean reviewer = r.getReviewerId().equals(ctx.userId());
        boolean hrm = HrAccess.manages(ctx);
        boolean shared = Review.DONE.equals(r.getStatus()) || Review.ACKNOWLEDGED.equals(r.getStatus());
        boolean showReview = reviewer || hrm || shared;
        boolean open = ReviewCycle.OPEN.equals(c.getStatus());
        return new ReviewView(r.getId(), view(c, List.of()), people.get(r.getEmployeeId()), people.get(r.getReviewerId()),
                r.getStatus(), answers(r.getSelfAnswers(), true), r.getSelfSummary(), r.getSelfSubmittedAt(),
                answers(r.getManagerAnswers(), showReview), showReview ? r.getManagerSummary() : null,
                showReview && r.getOverallRating() != null ? (int) r.getOverallRating() : null, r.getManagerSubmittedAt(),
                r.getAcknowledgedAt(), open && employee && Review.SELF.equals(r.getStatus()),
                open && reviewer && Review.MANAGER.equals(r.getStatus()), employee && Review.DONE.equals(r.getStatus()),
                open && hrm && !shared);
    }

    private List<ReviewRow> rowsWithCycles(OrgContext ctx, List<Review> found, boolean asEmployee) {
        Map<UUID, ReviewCycle> byId = cycles.findAllById(found.stream().map(Review::getCycleId).distinct().toList()).stream()
                .collect(Collectors.toMap(ReviewCycle::getId, Function.identity()));
        List<ReviewRow> out = rows(found, byId);
        if (asEmployee) {
            // The rating is the person's to see only once the review is shared.
            out = out.stream().map(x -> List.of(Review.DONE, Review.ACKNOWLEDGED).contains(x.status()) ? x
                    : new ReviewRow(x.id(), x.cycleId(), x.cycleName(), x.dueDate(), x.cycleStatus(), x.employee(), x.reviewer(),
                            x.status(), null)).toList();
        }
        return out.stream().sorted((a, b) -> b.dueDate().compareTo(a.dueDate())).toList();
    }

    private List<ReviewRow> rows(List<Review> found, Map<UUID, ReviewCycle> byId) {
        Map<UUID, PersonRef> people = profiles.persons(found.stream()
                .flatMap(r -> Stream.of(r.getEmployeeId(), r.getReviewerId())).filter(Objects::nonNull).distinct().toList());
        return found.stream().map(r -> {
            ReviewCycle c = byId.get(r.getCycleId());
            return new ReviewRow(r.getId(), c.getId(), c.getName(), c.getDueDate(), c.getStatus(), people.get(r.getEmployeeId()),
                    people.get(r.getReviewerId()), r.getStatus(), r.getOverallRating() == null ? null : (int) r.getOverallRating());
        }).sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.employee() == null ? "" : a.employee().name(),
                b.employee() == null ? "" : b.employee().name())).toList();
    }
}
