package com.businessplatform.education.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.AttendanceMark;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.payload.EducationPayloads.AttendanceCounts;
import com.businessplatform.education.payload.EducationPayloads.AssessmentView;
import com.businessplatform.education.payload.EducationPayloads.ClassSummary;
import com.businessplatform.education.payload.EducationPayloads.Overview;
import com.businessplatform.education.payload.EducationPayloads.WeekRate;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.AttendanceRepository;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * The education home: active students, teachers and classes; today's attendance (rate, and the classes whose
 * register isn't taken yet on a school day); the attendance rate of the last {@value #WEEKS} weeks; fees owed and
 * overdue (admins); assessments in the next {@value #UPCOMING_DAYS} days; withdrawal requests waiting. Admins see
 * the whole school, teachers their own classes.
 */
@Service
@RequiredArgsConstructor
public class EduOverviewService {

    static final int WEEKS = 8;
    static final int UPCOMING_DAYS = 14;

    private final EducationAccess access;
    private final StructureService structure;
    private final ClassService classService;
    private final GradebookService gradebook;
    private final WithdrawalService withdrawals;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AttendanceRepository marks;
    private final CourseRepository courses;
    private final AssessmentRepository assessments;
    private final FeeInvoiceRepository invoices;

    @Transactional(readOnly = true)
    public Overview overview(String slug) {
        Edu edu = access.require(slug);
        LocalDate today = access.today();
        AcademicYear year = structure.currentYear(edu.orgId()).orElse(null);
        Set<UUID> mine = access.classesTaughtBy(edu.teacherId());
        List<SchoolClass> scope = year == null ? List.of() : classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year.getId()).stream()
                .filter(c -> edu.admin() || mine.contains(c.getId())).toList();
        List<UUID> ids = scope.stream().map(SchoolClass::getId).toList();

        long active;
        if (edu.admin()) {
            active = students.countByOrgIdAndStatus(edu.orgId(), Student.ACTIVE);
        } else {
            Set<UUID> in = new HashSet<>();
            if (!ids.isEmpty()) {
                enrollments.findByClassIdIn(ids).stream().filter(e -> e.getLeftOn() == null).map(Enrollment::getStudentId).forEach(in::add);
            }
            active = in.size();
        }
        long teacherCount = teachers.findByOrgId(edu.orgId()).stream().filter(Teacher::isActive).count();

        boolean schoolDay = year != null && year.covers(today) && today.getDayOfWeek() != DayOfWeek.SUNDAY;
        List<AttendanceMark> todays = ids.isEmpty() ? List.of() : marks.findByClassIdInAndDayBetween(ids, today, today);
        Set<UUID> marked = new HashSet<>(todays.stream().map(AttendanceMark::getClassId).toList());
        List<SchoolClass> unmarked = schoolDay ? scope.stream().filter(c -> !marked.contains(c.getId()))
                .filter(c -> enrollments.countCurrent(c.getId()) > 0).toList() : List.of();
        List<ClassSummary> unmarkedViews = classService.summaries(edu, unmarked);
        java.math.BigDecimal rateToday = AttendanceService.counts(todays).rate();

        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate first = thisWeek.minusWeeks(WEEKS - 1);
        List<AttendanceMark> recent = ids.isEmpty() ? List.of() : marks.findByClassIdInAndDayBetween(ids, first, today);
        List<WeekRate> weeks = new ArrayList<>();
        for (LocalDate w = first; !w.isAfter(thisWeek); w = w.plusWeeks(1)) {
            LocalDate start = w;
            LocalDate end = w.plusDays(6);
            AttendanceCounts c = AttendanceService.counts(recent.stream().filter(m -> !m.getDay().isBefore(start) && !m.getDay().isAfter(end)).toList());
            weeks.add(new WeekRate(w, c.present(), c.late(), c.absent(), c.excused(), c.rate()));
        }

        List<Course> scopeCourses = ids.isEmpty() ? List.of() : courses.findByClassIdIn(ids).stream()
                .filter(c -> edu.admin() || EducationAccess.teaches(edu, c)).toList();
        Set<UUID> courseIds = new HashSet<>(scopeCourses.stream().map(Course::getId).toList());
        List<AssessmentView> upcoming = gradebook.views(edu, assessments.findByOrgIdAndHeldOnBetweenOrderByHeldOn(edu.orgId(), today,
                today.plusDays(UPCOMING_DAYS)).stream().filter(a -> courseIds.contains(a.getCourseId())).limit(10).toList());

        java.math.BigDecimal outstanding = null;
        java.math.BigDecimal overdue = null;
        Long overdueCount = null;
        if (edu.admin()) {
            outstanding = EduRefs.money(invoices.outstanding(edu.orgId()).get(0)[1]);
            Object[] late = invoices.overdue(edu.orgId(), today).get(0);
            overdue = EduRefs.money(late[1]);
            overdueCount = ((Number) late[0]).longValue();
        }
        return new Overview(edu.admin(), year == null ? null : structure.view(year), active, teacherCount, scope.size(),
                rateToday, marked.size(), unmarkedViews, weeks, outstanding, overdue, overdueCount, upcoming, withdrawals.waiting(edu));
    }
}
