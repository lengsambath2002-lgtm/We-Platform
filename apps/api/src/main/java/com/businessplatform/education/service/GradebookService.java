package com.businessplatform.education.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.businessplatform.education.domain.Assessment;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.Grading;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Score;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.Term;
import com.businessplatform.education.payload.EducationPayloads.AssessmentRequest;
import com.businessplatform.education.payload.EducationPayloads.AssessmentView;
import com.businessplatform.education.payload.EducationPayloads.ClassResults;
import com.businessplatform.education.payload.EducationPayloads.CourseRef;
import com.businessplatform.education.payload.EducationPayloads.Gradebook;
import com.businessplatform.education.payload.EducationPayloads.GradebookRow;
import com.businessplatform.education.payload.EducationPayloads.ReportCard;
import com.businessplatform.education.payload.EducationPayloads.ReportCardLine;
import com.businessplatform.education.payload.EducationPayloads.ResultRow;
import com.businessplatform.education.payload.EducationPayloads.ScoreEntry;
import com.businessplatform.education.payload.EducationPayloads.ScoresRequest;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.AttendanceRepository;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.ScoreRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * Assessments (exams, quizzes, assignments … with a maximum score and a weight) of a course in a term, the
 * gradebook where the course's teacher enters scores, and the results the server computes from them ({@link Grading}):
 * course results, term averages, letters, ranks in the class and the printable report card. The course's teacher
 * or an admin changes assessments and scores; the class's teachers see its results.
 */
@Service
@RequiredArgsConstructor
public class GradebookService {

    public static final String RECORD = "edu_assessment";

    private final EducationAccess access;
    private final StructureService structure;
    private final EduRefs refs;
    private final AssessmentRepository assessments;
    private final ScoreRepository scores;
    private final CourseRepository courses;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final AttendanceRepository marks;
    private final ActivityService activity;
    private final Clock clock;

    // ── Assessments ──────────────────────────────────────────────────────────────────

    /**
     * Assessments of one course ({@code courseId}) or of every course the caller may change in the current year
     * (admins: all), optionally in one term; newest first.
     */
    @Transactional(readOnly = true)
    public List<AssessmentView> list(String slug, UUID courseId, UUID termId) {
        Edu edu = access.require(slug);
        List<Course> cs;
        if (courseId != null) {
            Course c = course(edu, courseId);
            if (!seesCourse(edu, c)) {
                throw BusinessException.notFound();
            }
            cs = List.of(c);
        } else {
            UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
            Set<UUID> yearClasses = year == null ? Set.of()
                    : new HashSet<>(classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year).stream().map(SchoolClass::getId).toList());
            cs = (edu.admin() ? courses.findByOrgId(edu.orgId()) : courses.findByTeacherId(edu.teacherId())).stream()
                    .filter(c -> yearClasses.contains(c.getClassId())).toList();
        }
        if (cs.isEmpty()) {
            return List.of();
        }
        List<Assessment> found = assessments.findByCourseIdInOrderByHeldOnDesc(cs.stream().map(Course::getId).toList()).stream()
                .filter(a -> termId == null || a.getTermId().equals(termId)).toList();
        return views(edu, found);
    }

    @Transactional
    public AssessmentView save(String slug, UUID courseId, UUID id, AssessmentRequest in) {
        Edu edu = access.require(slug);
        Assessment a;
        Course c;
        if (id == null) {
            c = course(edu, courseId);
            a = new Assessment();
            a.setId(Ids.next());
            a.setOrgId(edu.orgId());
            a.setCourseId(c.getId());
            a.setCreatedBy(edu.userId());
            a.setCreatedAt(clock.instant());
        } else {
            a = assessment(edu, id);
            c = courses.findById(a.getCourseId()).orElseThrow();
        }
        if (!EducationAccess.teaches(edu, c)) {
            throw id == null ? BusinessException.forbidden() : BusinessException.notFound();
        }
        SchoolClass cls = classes.findById(c.getClassId()).orElseThrow();
        Term t = structure.term(edu, in.termId());
        if (!t.getYearId().equals(cls.getYearId())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "termId", "Choose a term of the class's academic year");
        }
        if (!t.covers(in.heldOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "heldOn", "This date is outside the term");
        }
        BigDecimal others = assessments.findByCourseIdAndTermIdOrderByHeldOnAscNameAsc(c.getId(), t.getId()).stream()
                .filter(x -> !x.getId().equals(a.getId())).map(Assessment::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (others.add(in.weight()).compareTo(new BigDecimal(100)) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "weight", "The weights of this course's assessments in the term would add up to more than 100");
        }
        if (id != null && in.maxScore().compareTo(a.getMaxScore()) < 0
                && scores.findByAssessmentId(a.getId()).stream().anyMatch(s -> s.getScore().compareTo(in.maxScore()) > 0)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "maxScore", "Some scores are above this maximum");
        }
        a.setTermId(t.getId());
        a.setName(in.name().strip());
        a.setKind(in.kind());
        a.setHeldOn(in.heldOn());
        a.setMaxScore(in.maxScore());
        a.setWeight(in.weight());
        assessments.saveAndFlush(a);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, a.getId(), edu.userId(),
                id == null ? "education.assessment.created" : "education.assessment.updated",
                Map.of("name", a.getName(), "course", courseTitle(edu, c)));
        return views(edu, List.of(a)).get(0);
    }

    @Transactional
    public void delete(String slug, UUID id) {
        Edu edu = access.require(slug);
        Assessment a = assessment(edu, id);
        Course c = courses.findById(a.getCourseId()).orElseThrow();
        if (!EducationAccess.teaches(edu, c)) {
            throw BusinessException.notFound();
        }
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, a.getId(), edu.userId(), "education.assessment.deleted",
                Map.of("name", a.getName(), "course", courseTitle(edu, c), "scores", scores.countByAssessmentId(a.getId())));
        assessments.delete(a);
    }

    // ── Gradebook ────────────────────────────────────────────────────────────────────

    /** A course's assessments in a term with every student's scores and computed result. */
    @Transactional(readOnly = true)
    public Gradebook gradebook(String slug, UUID courseId, UUID termId) {
        Edu edu = access.require(slug);
        Course c = course(edu, courseId);
        if (!seesCourse(edu, c)) {
            throw BusinessException.notFound();
        }
        Term t = structure.term(edu, termId);
        List<Assessment> as = assessments.findByCourseIdAndTermIdOrderByHeldOnAscNameAsc(c.getId(), t.getId());
        List<Score> ss = as.isEmpty() ? List.of() : scores.findByAssessmentIdIn(as.stream().map(Assessment::getId).toList());
        List<Student> roster = roster(edu, c.getClassId(), ss);
        List<GradebookRow> rows = roster.stream().map(s -> {
            Map<UUID, BigDecimal> mine = new LinkedHashMap<>();
            ss.stream().filter(x -> x.getStudentId().equals(s.getId())).forEach(x -> mine.put(x.getAssessmentId(), x.getScore()));
            BigDecimal result = Grading.courseResult(as.stream().map(a -> new Grading.Mark(mine.get(a.getId()), a.getMaxScore(), a.getWeight())).toList());
            return new GradebookRow(EduRefs.student(s), mine, result, Grading.letter(result));
        }).toList();
        BigDecimal total = as.stream().map(Assessment::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Gradebook(refs.courses(edu.orgId(), List.of(c)).get(c.getId()), EduRefs.term(t), views(edu, as), rows, total,
                Grading.average(rows.stream().map(GradebookRow::result).toList()), EducationAccess.teaches(edu, c));
    }

    /** Enters or clears scores of one assessment (the course's teacher or an admin). */
    @Transactional
    public Gradebook saveScores(String slug, UUID assessmentId, ScoresRequest in) {
        Edu edu = access.require(slug);
        Assessment a = assessment(edu, assessmentId);
        Course c = courses.findById(a.getCourseId()).orElseThrow();
        if (!EducationAccess.teaches(edu, c)) {
            throw seesCourse(edu, c) ? BusinessException.forbidden() : BusinessException.notFound();
        }
        Set<UUID> inClass = new HashSet<>(enrollments.findByClassId(c.getClassId()).stream().map(Enrollment::getStudentId).toList());
        Map<UUID, Score> existing = new HashMap<>();
        scores.findByAssessmentId(a.getId()).forEach(s -> existing.put(s.getStudentId(), s));
        int entered = 0;
        int cleared = 0;
        Set<UUID> seen = new HashSet<>();
        for (ScoreEntry e : in.scores()) {
            if (!inClass.contains(e.studentId()) || !seen.add(e.studentId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "scores", "A student on this list isn't in the class");
            }
            Score s = existing.get(e.studentId());
            if (e.score() == null) {
                if (s != null) {
                    scores.delete(s);
                    cleared++;
                }
                continue;
            }
            if (e.score().compareTo(a.getMaxScore()) > 0) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "scores", "A score is above the maximum");
            }
            if (s == null) {
                s = new Score();
                s.setAssessmentId(a.getId());
                s.setStudentId(e.studentId());
                s.setOrgId(edu.orgId());
            }
            s.setScore(e.score());
            s.setEnteredBy(edu.userId());
            s.setUpdatedAt(clock.instant());
            scores.save(s);
            entered++;
        }
        scores.flush();
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, a.getId(), edu.userId(), "education.scores.entered",
                Map.of("name", a.getName(), "course", courseTitle(edu, c), "count", entered, "cleared", cleared));
        return gradebook(slug, c.getId(), a.getTermId());
    }

    // ── Results ──────────────────────────────────────────────────────────────────────

    /** Every student's course results, term average, letter, rank and pass / fail in a class for a term. */
    @Transactional
    public ClassResults classResults(String slug, UUID classId, UUID termId) {
        Edu edu = access.require(slug);
        SchoolClass c = classes.findByIdAndOrgId(classId, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.teaches(edu, c)) {
            throw BusinessException.notFound();
        }
        Term t = structure.term(edu, termId);
        Results r = results(edu, c, t);
        BigDecimal pass = structure.settings(edu).getPassMark();
        List<ResultRow> rows = r.students().stream().map(s -> {
            BigDecimal avg = r.averages().get(s.getId());
            return new ResultRow(EduRefs.student(s), r.byStudent().getOrDefault(s.getId(), Map.of()), avg, Grading.letter(avg),
                    r.ranks().get(s.getId()), avg == null ? null : avg.compareTo(pass) >= 0);
        }).toList();
        Map<UUID, CourseRef> cr = refs.courses(edu.orgId(), r.courses());
        return new ClassResults(EduRefs.classRef(c), EduRefs.term(t), r.courses().stream().map(x -> cr.get(x.getId())).toList(), rows, pass);
    }

    /** The printable report card of a student for a term (their class in that term's year). */
    @Transactional
    public ReportCard reportCard(String slug, UUID studentId, UUID termId) {
        Edu edu = access.require(slug);
        Student s = students.findByIdAndOrgId(studentId, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.sees(edu, s.getId())) {
            throw BusinessException.notFound();
        }
        Term t = structure.term(edu, termId);
        Enrollment e = enrollments.findByStudentIdAndYearId(s.getId(), t.getYearId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "The student wasn't in a class that year", List.of()));
        SchoolClass c = classes.findById(e.getClassId()).orElseThrow();
        Results r = results(edu, c, t);
        Map<UUID, CourseRef> cr = refs.courses(edu.orgId(), r.courses());
        Map<UUID, BigDecimal> mine = r.byStudent().getOrDefault(s.getId(), Map.of());
        List<ReportCardLine> lines = r.courses().stream().map(x -> {
            CourseRef ref = cr.get(x.getId());
            BigDecimal res = mine.get(x.getId());
            return new ReportCardLine(ref.subject(), ref.teacher() == null ? null : ref.teacher().name(), res, Grading.letter(res));
        }).sorted(Comparator.comparing(l -> l.subject().nameEn())).toList();
        BigDecimal avg = r.averages().get(s.getId());
        BigDecimal pass = structure.settings(edu).getPassMark();
        AcademicYear y = structure.year(edu, t.getYearId());
        String homeroom = c.getHomeroomTeacherId() == null ? null : refs.teacher(c.getHomeroomTeacherId()).name();
        var counts = AttendanceService.counts(marks.findByClassIdInAndDayBetween(List.of(c.getId()), t.getStartsOn(), t.getEndsOn())
                .stream().filter(m -> m.getStudentId().equals(s.getId())).toList());
        return new ReportCard(StructureService.view(edu, structure.settings(edu)), StudentService.view(s), EduRefs.classRef(c),
                EduRefs.gradeName(structure.grade(edu, c.getGradeId())), homeroom, y.getName(), EduRefs.term(t), lines, avg,
                Grading.letter(avg), r.ranks().get(s.getId()), r.students().size(), avg == null ? null : avg.compareTo(pass) >= 0, counts);
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** Computed results of a class in a term. */
    record Results(List<Course> courses, List<Student> students, Map<UUID, Map<UUID, BigDecimal>> byStudent,
            Map<UUID, BigDecimal> averages, Map<UUID, Integer> ranks) {
    }

    @Transactional(readOnly = true)
    Results results(Edu edu, SchoolClass c, Term t) {
        List<Course> cs = courses.findByClassId(c.getId());
        List<Assessment> as = cs.isEmpty() ? List.of() : assessments.findByCourseIdInAndTermId(cs.stream().map(Course::getId).toList(), t.getId());
        List<Score> ss = as.isEmpty() ? List.of() : scores.findByAssessmentIdIn(as.stream().map(Assessment::getId).toList());
        List<Student> roster = roster(edu, c.getId(), ss);
        Map<UUID, Map<UUID, BigDecimal>> byStudent = new HashMap<>();
        Map<UUID, BigDecimal> averages = new HashMap<>();
        for (Student s : roster) {
            Map<UUID, BigDecimal> scoreOf = new HashMap<>();
            ss.stream().filter(x -> x.getStudentId().equals(s.getId())).forEach(x -> scoreOf.put(x.getAssessmentId(), x.getScore()));
            Map<UUID, BigDecimal> perCourse = new LinkedHashMap<>();
            for (Course course : cs) {
                BigDecimal res = Grading.courseResult(as.stream().filter(a -> a.getCourseId().equals(course.getId()))
                        .map(a -> new Grading.Mark(scoreOf.get(a.getId()), a.getMaxScore(), a.getWeight())).toList());
                if (res != null) {
                    perCourse.put(course.getId(), res);
                }
            }
            byStudent.put(s.getId(), perCourse);
            averages.put(s.getId(), Grading.average(perCourse.values()));
        }
        return new Results(cs, roster, byStudent, averages, Grading.ranks(averages));
    }

    private boolean seesCourse(Edu edu, Course c) {
        return EducationAccess.teaches(edu, c) || classes.findById(c.getClassId()).map(x -> access.teaches(edu, x)).orElse(false);
    }

    /** Current students of the class, plus anyone who left but has scores. By name. */
    private List<Student> roster(Edu edu, UUID classId, Collection<Score> ss) {
        Set<UUID> ids = new HashSet<>();
        enrollments.findByClassId(classId).stream().filter(e -> e.getLeftOn() == null).forEach(e -> ids.add(e.getStudentId()));
        ss.forEach(s -> ids.add(s.getStudentId()));
        return new ArrayList<>(students.findByIdInAndOrgId(ids, edu.orgId())).stream()
                .sorted(Comparator.comparing(Student::getNameEn, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private Course course(Edu edu, UUID id) {
        return courses.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    private Assessment assessment(Edu edu, UUID id) {
        return assessments.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** "Mathematics · 7A". */
    String courseTitle(Edu edu, Course c) {
        CourseRef r = refs.courses(edu.orgId(), List.of(c)).get(c.getId());
        return (r.subject() == null ? "" : r.subject().nameEn()) + " · " + (r.classRef() == null ? "" : r.classRef().name());
    }

    List<AssessmentView> views(Edu edu, List<Assessment> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Course> cs = new HashMap<>();
        courses.findAllById(found.stream().map(Assessment::getCourseId).distinct().toList()).forEach(c -> cs.put(c.getId(), c));
        Map<UUID, CourseRef> cr = refs.courses(edu.orgId(), cs.values());
        Map<UUID, Long> graded = new HashMap<>();
        scores.findByAssessmentIdIn(found.stream().map(Assessment::getId).toList()).forEach(s -> graded.merge(s.getAssessmentId(), 1L, Long::sum));
        Map<UUID, Long> classSize = new HashMap<>();
        Map<UUID, Term> terms = new HashMap<>();
        return found.stream().map(a -> {
            Course c = cs.get(a.getCourseId());
            long size = classSize.computeIfAbsent(c.getClassId(), k -> enrollments.countCurrent(k));
            Term t = terms.computeIfAbsent(a.getTermId(), k -> structure.term(edu, k));
            return new AssessmentView(a.getId(), cr.get(c.getId()), EduRefs.term(t), a.getName(), a.getKind(), a.getHeldOn(),
                    a.getMaxScore(), a.getWeight(), graded.getOrDefault(a.getId(), 0L), size, EducationAccess.teaches(edu, c));
        }).toList();
    }
}
