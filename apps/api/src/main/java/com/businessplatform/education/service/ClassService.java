package com.businessplatform.education.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.Subject;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.payload.EducationPayloads.ClassDetail;
import com.businessplatform.education.payload.EducationPayloads.ClassRequest;
import com.businessplatform.education.payload.EducationPayloads.ClassSummary;
import com.businessplatform.education.payload.EducationPayloads.CourseRequest;
import com.businessplatform.education.payload.EducationPayloads.CourseView;
import com.businessplatform.education.payload.EducationPayloads.EnrollRequest;
import com.businessplatform.education.payload.EducationPayloads.TeacherRef;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.PeriodRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;
import com.businessplatform.notification.NotificationService;

import lombok.RequiredArgsConstructor;

/**
 * Classes (a section of a grade level for an academic year, with a homeroom teacher), the students enrolled in
 * them, and their courses (a subject taught by one teacher). Admins arrange them; teachers see the classes they
 * teach. Teachers hear when they get a homeroom class or a course.
 */
@Service
@RequiredArgsConstructor
public class ClassService {

    public static final String RECORD = "edu_class";

    private final EducationAccess access;
    private final StructureService structure;
    private final TeacherService teacherService;
    private final StudentService studentService;
    private final TimetableService timetable;
    private final Enrollments enrollmentService;
    private final EduRefs refs;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;
    private final PeriodRepository periods;
    private final AssessmentRepository assessments;
    private final StudentRepository students;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    /** The classes of {@code yearId} (the current year when null), by name; teachers get theirs only. */
    @Transactional(readOnly = true)
    public List<ClassSummary> list(String slug, UUID yearId) {
        Edu edu = access.require(slug);
        UUID year = yearId != null ? structure.year(edu, yearId).getId()
                : structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
        if (year == null) {
            return List.of();
        }
        Set<UUID> mine = access.classesTaughtBy(edu.teacherId());
        List<SchoolClass> found = classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year).stream()
                .filter(c -> edu.admin() || mine.contains(c.getId())).toList();
        return summaries(edu, found);
    }

    @Transactional(readOnly = true)
    public ClassDetail get(String slug, UUID id) {
        Edu edu = access.require(slug);
        return detail(edu, visible(edu, id));
    }

    @Transactional
    public ClassDetail save(String slug, UUID id, ClassRequest in) {
        Edu edu = access.requireAdmin(slug);
        SchoolClass c;
        UUID oldHomeroom = null;
        if (id == null) {
            c = new SchoolClass();
            c.setId(Ids.next());
            c.setOrgId(edu.orgId());
            c.setCreatedAt(clock.instant());
            c.setYearId(structure.year(edu, in.yearId()).getId());
        } else {
            c = lock(edu, id);
            oldHomeroom = c.getHomeroomTeacherId();
            if (!c.getYearId().equals(in.yearId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "yearId", "A class stays in its academic year");
            }
        }
        GradeLevel g = structure.grade(edu, in.gradeId());
        String name = in.name().strip();
        if (classes.existsByYearIdAndNameIgnoreCaseAndIdNot(c.getYearId(), name, c.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another class this year has this name");
        }
        if (in.capacity() != null && id != null && enrollments.countCurrent(c.getId()) > in.capacity()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "capacity", "More students are already in this class");
        }
        Teacher homeroom = in.homeroomTeacherId() == null ? null : teacherService.usable(edu, in.homeroomTeacherId(), "homeroomTeacherId");
        c.setGradeId(g.getId());
        c.setName(name);
        c.setHomeroomTeacherId(homeroom == null ? null : homeroom.getId());
        c.setRoom(EduRefs.clean(in.room()));
        c.setCapacity(in.capacity() == null ? null : in.capacity().shortValue());
        classes.saveAndFlush(c);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(),
                id == null ? "education.class.created" : "education.class.updated", Map.of("name", c.getName()));
        if (homeroom != null && !homeroom.getId().equals(oldHomeroom)) {
            notifications.send(edu.orgId(), List.of(homeroom.getUserId()), edu.userId(), "education.class.homeroom",
                    Text.of("You are the homeroom teacher of {class}", Map.of("class", c.getName())), null, link(edu, c.getId()));
        }
        return detail(edu, c);
    }

    @Transactional
    public ClassDetail enroll(String slug, UUID id, EnrollRequest in) {
        Edu edu = access.requireAdmin(slug);
        SchoolClass c = lock(edu, id);
        List<String> names = new ArrayList<>();
        for (UUID sid : in.studentIds().stream().distinct().toList()) {
            Student s = students.findByIdAndOrgId(sid, edu.orgId())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "studentIds", "Choose a student"));
            if (enrollments.findByClassIdAndStudentId(c.getId(), sid).isPresent()) {
                continue;
            }
            enrollmentService.enroll(c, s, access.today());
            names.add(s.getNameEn());
        }
        if (!names.isEmpty()) {
            activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(), "education.class.enrolled",
                    Map.of("name", c.getName(), "count", names.size(), "students", String.join(", ", names)));
        }
        return detail(edu, c);
    }

    /** Takes a student out of the class (e.g. to move them to another class). */
    @Transactional
    public ClassDetail unenroll(String slug, UUID id, UUID studentId) {
        Edu edu = access.requireAdmin(slug);
        SchoolClass c = lock(edu, id);
        Enrollment e = enrollments.findByClassIdAndStudentId(c.getId(), studentId).orElseThrow(BusinessException::notFound);
        enrollments.delete(e);
        String name = students.findById(studentId).map(Student::getNameEn).orElse("");
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(), "education.class.unenrolled",
                Map.of("name", c.getName(), "student", name));
        return detail(edu, c);
    }

    // ── Courses ──────────────────────────────────────────────────────────────────────

    /** Courses of a class, or (no class) of the current year: everyone's for admins, the caller's own for teachers. */
    @Transactional(readOnly = true)
    public List<CourseView> courses(String slug, UUID classId) {
        Edu edu = access.require(slug);
        List<Course> found;
        if (classId != null) {
            found = courses.findByClassId(visible(edu, classId).getId());
        } else {
            UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
            List<UUID> ids = year == null ? List.of()
                    : classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year).stream().map(SchoolClass::getId).toList();
            found = ids.isEmpty() ? List.of() : courses.findByClassIdIn(ids).stream().filter(c -> EducationAccess.teaches(edu, c)).toList();
        }
        return courseViews(edu, found);
    }

    @Transactional
    public CourseView addCourse(String slug, UUID classId, CourseRequest in) {
        Edu edu = access.requireAdmin(slug);
        SchoolClass c = lock(edu, classId);
        Subject s = structure.subject(edu, in.subjectId());
        if (!s.isActive()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "subjectId", "This subject is switched off");
        }
        if (courses.existsByClassIdAndSubjectId(c.getId(), s.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "subjectId", "This class already has this subject");
        }
        Teacher t = teacherService.usable(edu, in.teacherId(), "teacherId");
        Course x = new Course();
        x.setId(Ids.next());
        x.setOrgId(edu.orgId());
        x.setClassId(c.getId());
        x.setSubjectId(s.getId());
        x.setTeacherId(t.getId());
        x.setCreatedAt(clock.instant());
        courses.saveAndFlush(x);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(), "education.course.created",
                Map.of("class", c.getName(), "subject", s.getNameEn()));
        notifyTeacher(edu, t, c, s);
        return courseViews(edu, List.of(x)).get(0);
    }

    /** Gives the course to another teacher; refused if they already teach elsewhere at one of its lesson times. */
    @Transactional
    public CourseView changeTeacher(String slug, UUID courseId, UUID teacherId) {
        Edu edu = access.requireAdmin(slug);
        Course x = courses.findByIdAndOrgId(courseId, edu.orgId()).orElseThrow(BusinessException::notFound);
        SchoolClass c = lock(edu, x.getClassId());
        Teacher t = teacherService.usable(edu, teacherId, "teacherId");
        if (t.getId().equals(x.getTeacherId())) {
            return courseViews(edu, List.of(x)).get(0);
        }
        timetable.checkTeacherFree(edu, c, x, t);
        x.setTeacherId(t.getId());
        courses.saveAndFlush(x);
        Subject s = structure.subject(edu, x.getSubjectId());
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(), "education.course.updated",
                Map.of("class", c.getName(), "subject", s.getNameEn(), "teacher", refs.teacher(t.getId()).name()));
        notifyTeacher(edu, t, c, s);
        return courseViews(edu, List.of(x)).get(0);
    }

    @Transactional
    public void removeCourse(String slug, UUID courseId) {
        Edu edu = access.requireAdmin(slug);
        Course x = courses.findByIdAndOrgId(courseId, edu.orgId()).orElseThrow(BusinessException::notFound);
        SchoolClass c = lock(edu, x.getClassId());
        if (!assessments.findByCourseIdOrderByHeldOnAscNameAsc(x.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This course has assessments; it can't be removed", List.of());
        }
        Subject s = structure.subject(edu, x.getSubjectId());
        courses.delete(x);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, c.getId(), edu.userId(), "education.course.deleted",
                Map.of("class", c.getName(), "subject", s.getNameEn()));
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** The class if the caller teaches it (or is an admin); NOT_FOUND otherwise. */
    SchoolClass visible(Edu edu, UUID id) {
        SchoolClass c = classes.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.teaches(edu, c)) {
            throw BusinessException.notFound();
        }
        return c;
    }

    private SchoolClass lock(Edu edu, UUID id) {
        return classes.lock(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    static String link(Edu edu, UUID id) {
        return "/" + edu.slug() + "/education/classes/" + id;
    }

    private void notifyTeacher(Edu edu, Teacher t, SchoolClass c, Subject s) {
        notifications.send(edu.orgId(), List.of(t.getUserId()), edu.userId(), "education.course.assigned",
                Text.of("You teach {subject} in {class}", Map.of("subject", s.getNameEn(), "class", c.getName())), null,
                link(edu, c.getId()));
    }

    List<ClassSummary> summaries(Edu edu, List<SchoolClass> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        var years = refs.years(edu.orgId());
        Map<UUID, GradeLevel> grades = refs.grades(edu.orgId());
        Map<UUID, TeacherRef> ts = refs.teachers(found.stream().map(SchoolClass::getHomeroomTeacherId).filter(Objects::nonNull).toList());
        List<UUID> ids = found.stream().map(SchoolClass::getId).toList();
        Map<UUID, Long> counts = new HashMap<>();
        enrollments.findByClassIdIn(ids).stream().filter(e -> e.getLeftOn() == null).forEach(e -> counts.merge(e.getClassId(), 1L, Long::sum));
        List<Course> cs = courses.findByClassIdIn(ids);
        Set<UUID> mine = access.classesTaughtBy(edu.teacherId());
        return found.stream().map(c -> new ClassSummary(c.getId(), c.getName(), c.getYearId(),
                years.containsKey(c.getYearId()) ? years.get(c.getYearId()).getName() : "", c.getGradeId(),
                EduRefs.gradeName(grades.get(c.getGradeId())), c.getHomeroomTeacherId() == null ? null : ts.get(c.getHomeroomTeacherId()),
                c.getRoom(), c.getCapacity() == null ? null : c.getCapacity().intValue(), counts.getOrDefault(c.getId(), 0L),
                (int) cs.stream().filter(x -> x.getClassId().equals(c.getId())).count(), mine.contains(c.getId())))
                .sorted(Comparator.comparing((ClassSummary s) -> grades.containsKey(s.gradeId()) ? grades.get(s.gradeId()).getPosition() : 0)
                        .thenComparing(ClassSummary::name))
                .toList();
    }

    List<CourseView> courseViews(Edu edu, List<Course> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        var cs = refs.classes(edu.orgId(), found.stream().map(Course::getClassId).toList());
        var ss = refs.subjects(edu.orgId());
        var ts = refs.teachers(found.stream().map(Course::getTeacherId).toList());
        Map<UUID, Long> perCourse = new HashMap<>();
        periods.findByCourseIdIn(found.stream().map(Course::getId).toList()).forEach(p -> perCourse.merge(p.getCourseId(), 1L, Long::sum));
        return found.stream().map(x -> new CourseView(x.getId(), EduRefs.classRef(cs.get(x.getClassId())),
                EduRefs.subject(ss.get(x.getSubjectId())), ts.get(x.getTeacherId()), perCourse.getOrDefault(x.getId(), 0L).intValue(),
                edu.teacherId() != null && edu.teacherId().equals(x.getTeacherId())))
                .sorted(Comparator.comparing((CourseView v) -> v.classRef() == null ? "" : v.classRef().name())
                        .thenComparing(v -> v.subject() == null ? "" : v.subject().nameEn()))
                .toList();
    }

    private ClassDetail detail(Edu edu, SchoolClass c) {
        List<UUID> ids = enrollments.findByClassId(c.getId()).stream().filter(e -> e.getLeftOn() == null).map(Enrollment::getStudentId).toList();
        List<Student> ss = students.findByIdInAndOrgId(ids, edu.orgId()).stream()
                .sorted(Comparator.comparing(Student::getNameEn, String.CASE_INSENSITIVE_ORDER)).toList();
        return new ClassDetail(summaries(edu, List.of(c)).get(0), studentService.summaries(edu, ss),
                courseViews(edu, courses.findByClassId(c.getId())), edu.admin(), access.teaches(edu, c));
    }
}
