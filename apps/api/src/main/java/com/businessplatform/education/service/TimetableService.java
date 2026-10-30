package com.businessplatform.education.service;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.Period;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.payload.EducationPayloads.PeriodRequest;
import com.businessplatform.education.payload.EducationPayloads.PeriodView;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.PeriodRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * The weekly timetable: lessons of a class's courses, Monday to Saturday. Admins plan it; teachers see their
 * classes' and their own. The server refuses double bookings within an academic year — a class with two lessons
 * at once, a teacher in two places at once, or a room used twice at once — taking the class and the teacher
 * under a row lock so two planners can't book the same slot together.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    private final EducationAccess access;
    private final StructureService structure;
    private final EduRefs refs;
    private final PeriodRepository periods;
    private final CourseRepository courses;
    private final SchoolClassRepository classes;
    private final TeacherRepository teachers;
    private final ActivityService activity;

    /**
     * One class's week ({@code classId}), one teacher's ({@code teacherId}; a teacher may ask for their own), or,
     * with neither, the caller's: every class of the current year for admins, a teacher's own lessons.
     */
    @Transactional(readOnly = true)
    public List<PeriodView> list(String slug, UUID classId, UUID teacherId) {
        Edu edu = access.require(slug);
        List<Period> found;
        if (classId != null) {
            SchoolClass c = classes.findByIdAndOrgId(classId, edu.orgId()).orElseThrow(BusinessException::notFound);
            if (!access.teaches(edu, c)) {
                throw BusinessException.notFound();
            }
            found = periods.findByClassId(c.getId());
        } else {
            UUID who = teacherId != null ? teacherId : edu.admin() ? null : edu.teacherId();
            if (who != null && !edu.admin() && !who.equals(edu.teacherId())) {
                throw BusinessException.forbidden();
            }
            UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
            if (year == null) {
                return List.of();
            }
            Set<UUID> yearClasses = new java.util.HashSet<>(classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year).stream()
                    .map(SchoolClass::getId).toList());
            List<Course> cs = (who == null ? courses.findByOrgId(edu.orgId()) : courses.findByTeacherId(who)).stream()
                    .filter(c -> yearClasses.contains(c.getClassId())).toList();
            found = cs.isEmpty() ? List.of() : periods.findByCourseIdIn(cs.stream().map(Course::getId).toList());
        }
        return views(edu, found);
    }

    @Transactional
    public PeriodView save(String slug, UUID id, PeriodRequest in) {
        Edu edu = access.requireAdmin(slug);
        Course course = courses.findByIdAndOrgId(in.courseId(), edu.orgId())
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "courseId", "Choose a course"));
        SchoolClass c = classes.lock(course.getClassId(), edu.orgId()).orElseThrow();
        Teacher t = teachers.lock(course.getTeacherId(), edu.orgId()).orElseThrow();
        Period p;
        if (id == null) {
            p = new Period();
            p.setId(Ids.next());
            p.setOrgId(edu.orgId());
        } else {
            p = periods.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
        }
        if (!in.endsAt().isAfter(in.startsAt())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsAt", "The end is before the start");
        }
        short day = in.weekday().shortValue();
        String room = EduRefs.clean(in.room());
        check(edu, c, t, p.getId(), day, in.startsAt(), in.endsAt(), room);
        p.setClassId(c.getId());
        p.setCourseId(course.getId());
        p.setWeekday(day);
        p.setStartsAt(in.startsAt());
        p.setEndsAt(in.endsAt());
        p.setRoom(room);
        periods.saveAndFlush(p);
        String subject = structure.subject(edu, course.getSubjectId()).getNameEn();
        activity.record(edu.orgId(), EducationAccess.MODULE, ClassService.RECORD, c.getId(), edu.userId(),
                id == null ? "education.period.added" : "education.period.updated",
                Map.of("class", c.getName(), "subject", subject, "weekday", (int) day, "start", in.startsAt().toString(),
                        "end", in.endsAt().toString()));
        return views(edu, List.of(p)).get(0);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        Edu edu = access.requireAdmin(slug);
        Period p = periods.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
        SchoolClass c = classes.findById(p.getClassId()).orElseThrow();
        Course course = courses.findById(p.getCourseId()).orElseThrow();
        String subject = structure.subject(edu, course.getSubjectId()).getNameEn();
        periods.delete(p);
        activity.record(edu.orgId(), EducationAccess.MODULE, ClassService.RECORD, c.getId(), edu.userId(), "education.period.removed",
                Map.of("class", c.getName(), "subject", subject, "weekday", (int) p.getWeekday(), "start", p.getStartsAt().toString(),
                        "end", p.getEndsAt().toString()));
    }

    /** Before a course changes teacher: {@code to} must be free at every lesson of the course. */
    @Transactional
    public void checkTeacherFree(Edu edu, SchoolClass c, Course course, Teacher to) {
        teachers.lock(to.getId(), edu.orgId());
        Map<UUID, Course> byId = new HashMap<>();
        for (Period p : periods.findByCourseIdIn(List.of(course.getId()))) {
            for (Period other : periods.onDay(edu.orgId(), c.getYearId(), p.getWeekday())) {
                if (other.getCourseId().equals(course.getId()) || !other.overlaps(p.getWeekday(), p.getStartsAt(), p.getEndsAt())) {
                    continue;
                }
                Course oc = byId.computeIfAbsent(other.getCourseId(), k -> courses.findById(k).orElseThrow());
                if (oc.getTeacherId().equals(to.getId())) {
                    throw BusinessException.field(ErrorCode.CONFLICT, "teacherId",
                            "This teacher already teaches another class at one of this course's lesson times");
                }
            }
        }
    }

    private void check(Edu edu, SchoolClass c, Teacher t, UUID self, short day, LocalTime start, LocalTime end, String room) {
        Map<UUID, Course> byId = new HashMap<>();
        for (Period other : periods.onDay(edu.orgId(), c.getYearId(), day)) {
            if (other.getId().equals(self) || !other.overlaps(day, start, end)) {
                continue;
            }
            if (other.getClassId().equals(c.getId())) {
                throw BusinessException.field(ErrorCode.CONFLICT, "startsAt", "This class already has a lesson at this time");
            }
            Course oc = byId.computeIfAbsent(other.getCourseId(), k -> courses.findById(k).orElseThrow());
            if (oc.getTeacherId().equals(t.getId())) {
                throw BusinessException.field(ErrorCode.CONFLICT, "startsAt", "This teacher already teaches another class at this time");
            }
            if (room != null && other.getRoom() != null && other.getRoom().equalsIgnoreCase(room)) {
                throw BusinessException.field(ErrorCode.CONFLICT, "room", "This room is already in use at this time");
            }
        }
    }

    private List<PeriodView> views(Edu edu, List<Period> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Course> cs = new HashMap<>();
        courses.findAllById(found.stream().map(Period::getCourseId).distinct().toList()).forEach(c -> cs.put(c.getId(), c));
        var classMap = refs.classes(edu.orgId(), found.stream().map(Period::getClassId).toList());
        var subjects = refs.subjects(edu.orgId());
        var ts = refs.teachers(cs.values().stream().map(Course::getTeacherId).toList());
        return found.stream().map(p -> {
            Course c = cs.get(p.getCourseId());
            return new PeriodView(p.getId(), EduRefs.classRef(classMap.get(p.getClassId())), c.getId(),
                    EduRefs.subject(subjects.get(c.getSubjectId())), ts.get(c.getTeacherId()), p.getWeekday(), p.getStartsAt(),
                    p.getEndsAt(), p.getRoom(), edu.admin());
        }).sorted(Comparator.comparing(PeriodView::weekday).thenComparing(PeriodView::startsAt)
                .thenComparing(v -> v.classRef() == null ? "" : v.classRef().name())).toList();
    }
}
