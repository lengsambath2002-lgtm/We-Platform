package com.businessplatform.hr.service;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import com.businessplatform.hr.domain.TrainingCourse;
import com.businessplatform.hr.domain.TrainingEnrolment;
import com.businessplatform.hr.payload.DevelopmentPayloads.*;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;
import com.businessplatform.hr.repository.DevelopmentRepositories.TrainingCourseRepository;
import com.businessplatform.hr.repository.DevelopmentRepositories.TrainingEnrolmentRepository;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.OrgRole;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Training. HR managers publish courses and enrol people; anyone signs up for an open course that allows it
 * (within its capacity) and leaves it while enrolled. HR marks completion; everyone sees their training record.
 */
@Service
@RequiredArgsConstructor
public class TrainingService {

    /** How courses appear in the activity log. */
    public static final String COURSE = "course";

    private final HrAccess hr;
    private final ProfileService profiles;
    private final TrainingCourseRepository courses;
    private final TrainingEnrolmentRepository enrolments;
    private final MembershipRepository memberships;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    /** HR managers: every course; others: open courses and the ones they are on. */
    @Transactional(readOnly = true)
    public List<CourseView> courses(String slug) {
        OrgContext ctx = hr.require(slug);
        List<TrainingCourse> all = courses.findByOrgIdOrderByStartsOnDescCreatedAtDesc(ctx.orgId());
        Map<UUID, List<TrainingEnrolment>> byCourse = enrolments.findByCourseIdIn(all.stream().map(TrainingCourse::getId).toList())
                .stream().collect(Collectors.groupingBy(TrainingEnrolment::getCourseId));
        return all.stream().map(c -> view(ctx, c, byCourse.getOrDefault(c.getId(), List.of())))
                .filter(v -> HrAccess.manages(ctx) || TrainingCourse.OPEN.equals(v.status()) || v.myStatus() != null).toList();
    }

    @Transactional(readOnly = true)
    public CourseDetail course(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        TrainingCourse c = course(ctx, id);
        List<TrainingEnrolment> es = enrolments.findByCourseId(c.getId());
        CourseView v = view(ctx, c, es);
        if (!HrAccess.manages(ctx) && !TrainingCourse.OPEN.equals(c.getStatus()) && v.myStatus() == null) {
            throw BusinessException.notFound();
        }
        return new CourseDetail(v, HrAccess.manages(ctx) ? enrolmentViews(es) : List.of(), HrAccess.manages(ctx));
    }

    @Transactional
    public CourseDetail save(String slug, UUID id, CourseRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        TrainingCourse c = id == null ? new TrainingCourse() : course(ctx, id);
        if (in.startsOn() != null && in.endsOn() != null && in.endsOn().isBefore(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "The course ends before it starts");
        }
        if (id == null) {
            c.setId(Ids.next());
            c.setOrgId(ctx.orgId());
            c.setCreatedBy(ctx.userId());
            c.setCreatedAt(clock.instant());
        }
        c.setTitle(in.title().strip());
        c.setDescription(ProfileService.clean(in.description()));
        c.setProvider(ProfileService.clean(in.provider()));
        c.setLocation(ProfileService.clean(in.location()));
        c.setStartsOn(in.startsOn());
        c.setEndsOn(in.endsOn());
        c.setHours(in.hours());
        c.setCapacity(in.capacity() == null ? null : in.capacity().shortValue());
        c.setSelfEnrol(in.selfEnrol());
        c.setStatus(in.status() == null ? TrainingCourse.OPEN : in.status());
        c.setUpdatedAt(clock.instant());
        courses.saveAndFlush(c);
        activity.record(ctx.orgId(), HrAccess.MODULE, COURSE, c.getId(), ctx.userId(), id == null ? "training.course.created" : "training.course.updated",
                Map.of("title", c.getTitle()));
        return course(slug, c.getId());
    }

    /** Signs the caller up for an open, self-enrol course with room. */
    @Transactional
    public CourseDetail enrolMe(String slug, UUID id) {
        OrgContext ctx = hr.require(slug);
        TrainingCourse c = course(ctx, id);
        if (!TrainingCourse.OPEN.equals(c.getStatus()) || !c.isSelfEnrol()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This course isn't open for sign-up", List.of());
        }
        enrol(ctx, c, ctx.userId());
        return course(slug, id);
    }

    /** HR managers: enrols people (those already on it stay as they are). */
    @Transactional
    public CourseDetail enrolPeople(String slug, UUID id, EnrolRequest in) {
        OrgContext ctx = hr.requireManager(slug);
        TrainingCourse c = course(ctx, id);
        for (UUID u : in.userIds().stream().distinct().toList()) {
            boolean member = memberships.findByOrgIdAndUserId(ctx.orgId(), u)
                    .filter(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole() != OrgRole.GUEST).isPresent();
            if (!member) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "userIds", "Choose members of the organisation");
            }
            Optional<TrainingEnrolment> e = enrolments.findByCourseIdAndUserId(c.getId(), u);
            if (e.isEmpty() || TrainingEnrolment.CANCELLED.equals(e.get().getStatus())) {
                enrol(ctx, c, u);
                notifications.send(ctx.orgId(), List.of(u), ctx.userId(), "training.enrolled",
                        Text.of("You are enrolled in {title}", Map.of("title", c.getTitle())), null, link(ctx, c));
            }
        }
        return course(slug, id);
    }

    /**
     * Completes or cancels an enrolment (HR managers), or (the person) leaves a course they are enrolled in.
     */
    @Transactional
    public CourseDetail setStatus(String slug, UUID enrolmentId, EnrolmentStatusRequest in) {
        OrgContext ctx = hr.require(slug);
        TrainingEnrolment e = enrolments.findByIdAndOrgId(enrolmentId, ctx.orgId()).orElseThrow(BusinessException::notFound);
        boolean mine = e.getUserId().equals(ctx.userId());
        boolean leaving = mine && TrainingEnrolment.CANCELLED.equals(in.status()) && TrainingEnrolment.ENROLLED.equals(e.getStatus());
        if (!HrAccess.manages(ctx) && !leaving) {
            throw mine ? BusinessException.forbidden() : BusinessException.notFound();
        }
        TrainingCourse c = courses.findById(e.getCourseId()).orElseThrow();
        if (TrainingEnrolment.ENROLLED.equals(in.status()) && !TrainingEnrolment.ENROLLED.equals(e.getStatus())) {
            checkRoom(c);
        }
        e.setStatus(in.status());
        e.setCompletedAt(TrainingEnrolment.COMPLETED.equals(in.status()) ? clock.instant() : null);
        e.setUpdatedAt(clock.instant());
        if (TrainingEnrolment.COMPLETED.equals(in.status())) {
            activity.record(ctx.orgId(), HrAccess.MODULE, COURSE, c.getId(), ctx.userId(), "training.completed",
                    Map.of("title", c.getTitle(), "name", profiles.persons(List.of(e.getUserId())).get(e.getUserId()).name()));
        }
        return course(slug, c.getId());
    }

    /** My training record: every course I am or was on, newest first. */
    @Transactional(readOnly = true)
    public List<MyTraining> mine(String slug) {
        OrgContext ctx = hr.require(slug);
        List<TrainingEnrolment> mine = enrolments.findByOrgIdAndUserId(ctx.orgId(), ctx.userId());
        Map<UUID, TrainingCourse> byId = courses.findAllById(mine.stream().map(TrainingEnrolment::getCourseId).toList()).stream()
                .collect(Collectors.toMap(TrainingCourse::getId, Function.identity()));
        Map<UUID, List<TrainingEnrolment>> all = enrolments.findByCourseIdIn(byId.keySet()).stream()
                .collect(Collectors.groupingBy(TrainingEnrolment::getCourseId));
        return mine.stream().sorted(Comparator.comparing(TrainingEnrolment::getCreatedAt).reversed())
                .map(e -> new MyTraining(e.getId(), view(ctx, byId.get(e.getCourseId()), all.getOrDefault(e.getCourseId(), List.of())),
                        e.getStatus(), e.getCompletedAt())).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    private TrainingCourse course(OrgContext ctx, UUID id) {
        return courses.findByIdAndOrgId(id, ctx.orgId()).orElseThrow(BusinessException::notFound);
    }

    private void enrol(OrgContext ctx, TrainingCourse c, UUID userId) {
        TrainingEnrolment e = enrolments.findByCourseIdAndUserId(c.getId(), userId).orElse(null);
        if (e != null && !TrainingEnrolment.CANCELLED.equals(e.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Already on this course", List.of());
        }
        checkRoom(c);
        if (e == null) {
            e = new TrainingEnrolment();
            e.setId(Ids.next());
            e.setOrgId(ctx.orgId());
            e.setCourseId(c.getId());
            e.setUserId(userId);
            e.setCreatedAt(clock.instant());
        }
        e.setStatus(TrainingEnrolment.ENROLLED);
        e.setEnrolledBy(ctx.userId());
        e.setCompletedAt(null);
        e.setUpdatedAt(clock.instant());
        enrolments.saveAndFlush(e);
    }

    private void checkRoom(TrainingCourse c) {
        if (c.getCapacity() != null && taken(enrolments.findByCourseId(c.getId())) >= c.getCapacity()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This course is full", List.of());
        }
    }

    private static int taken(List<TrainingEnrolment> es) {
        return (int) es.stream().filter(e -> !TrainingEnrolment.CANCELLED.equals(e.getStatus())).count();
    }

    private static String link(OrgContext ctx, TrainingCourse c) {
        return "/" + ctx.slug() + "/hr/training/" + c.getId();
    }

    private CourseView view(OrgContext ctx, TrainingCourse c, List<TrainingEnrolment> es) {
        Optional<TrainingEnrolment> mine = es.stream().filter(e -> e.getUserId().equals(ctx.userId())).findFirst();
        boolean room = c.getCapacity() == null || taken(es) < c.getCapacity();
        boolean on = mine.map(e -> !TrainingEnrolment.CANCELLED.equals(e.getStatus())).orElse(false);
        return new CourseView(c.getId(), c.getTitle(), c.getDescription(), c.getProvider(), c.getLocation(), c.getStartsOn(),
                c.getEndsOn(), c.getHours(), c.getCapacity() == null ? null : (int) c.getCapacity(), c.isSelfEnrol(), c.getStatus(),
                taken(es), (int) es.stream().filter(e -> TrainingEnrolment.COMPLETED.equals(e.getStatus())).count(),
                mine.map(TrainingEnrolment::getStatus).orElse(null), mine.map(TrainingEnrolment::getId).orElse(null),
                TrainingCourse.OPEN.equals(c.getStatus()) && c.isSelfEnrol() && room && !on);
    }

    private List<EnrolmentView> enrolmentViews(List<TrainingEnrolment> es) {
        Map<UUID, PersonRef> people = profiles.persons(es.stream().flatMap(e -> Stream.of(e.getUserId(), e.getEnrolledBy()))
                .filter(Objects::nonNull).distinct().toList());
        return es.stream().map(e -> new EnrolmentView(e.getId(), people.get(e.getUserId()), e.getStatus(), people.get(e.getEnrolledBy()),
                e.getCompletedAt(), e.getCreatedAt()))
                .sorted(Comparator.comparing(v -> v.person() == null ? "" : v.person().name(), String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
