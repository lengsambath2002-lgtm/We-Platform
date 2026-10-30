package com.businessplatform.education.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import com.businessplatform.education.domain.AttendanceMark;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.Grading;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.payload.EducationPayloads.AttendanceCounts;
import com.businessplatform.education.payload.EducationPayloads.AttendanceEntry;
import com.businessplatform.education.payload.EducationPayloads.AttendanceRequest;
import com.businessplatform.education.payload.EducationPayloads.AttendanceRow;
import com.businessplatform.education.payload.EducationPayloads.AttendanceSheet;
import com.businessplatform.education.payload.EducationPayloads.AttendanceSummary;
import com.businessplatform.education.payload.EducationPayloads.AttendanceSummaryRow;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.AttendanceRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * Student attendance (separate from employee attendance in HR): one mark per student, class and day — present,
 * absent, late or excused — taken by the class's teachers (its homeroom teacher or a teacher of one of its courses)
 * or an admin, for today or an earlier day of the class's academic year. Taking it again changes the marks. The
 * rate counts present and late days against the days that count (excused days don't; see {@link Grading}).
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EducationAccess access;
    private final AttendanceRepository marks;
    private final EnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final StudentRepository students;
    private final AcademicYearRepository years;
    private final EduRefs refs;
    private final ActivityService activity;
    private final Clock clock;

    /** The class's register for {@code day}: everyone in the class that day, with their mark if taken. */
    @Transactional(readOnly = true)
    public AttendanceSheet sheet(String slug, UUID classId, LocalDate day) {
        Edu edu = access.require(slug);
        SchoolClass c = visible(edu, classId);
        return sheet(edu, c, day);
    }

    @Transactional
    public AttendanceSheet save(String slug, UUID classId, AttendanceRequest in) {
        Edu edu = access.require(slug);
        SchoolClass c = classes.lock(classId, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.teaches(edu, c)) {
            throw BusinessException.notFound();
        }
        LocalDate day = in.day();
        if (day.isAfter(access.today())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "day", "Attendance can't be taken for a future day");
        }
        AcademicYear y = years.findById(c.getYearId()).orElseThrow();
        if (!y.covers(day)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "day", "This day is outside the class's academic year");
        }
        Set<UUID> roster = new HashSet<>(roster(c, day));
        Map<UUID, AttendanceMark> existing = new HashMap<>();
        marks.findByClassIdAndDay(c.getId(), day).forEach(m -> existing.put(m.getStudentId(), m));
        Set<UUID> seen = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>();
        for (AttendanceEntry e : in.entries()) {
            if (!roster.contains(e.studentId()) || !seen.add(e.studentId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "entries", "A student on this list isn't in the class that day");
            }
            AttendanceMark m = existing.get(e.studentId());
            if (m == null) {
                m = new AttendanceMark();
                m.setId(Ids.next());
                m.setOrgId(edu.orgId());
                m.setClassId(c.getId());
                m.setStudentId(e.studentId());
                m.setDay(day);
            }
            m.setStatus(e.status());
            m.setNote(EduRefs.clean(e.note()));
            m.setRecordedBy(edu.userId());
            m.setRecordedAt(clock.instant());
            marks.save(m);
            counts.merge(e.status(), 1, Integer::sum);
        }
        marks.flush();
        activity.record(edu.orgId(), EducationAccess.MODULE, ClassService.RECORD, c.getId(), edu.userId(), "education.attendance.taken",
                Map.of("class", c.getName(), "date", day.toString(), "present", counts.getOrDefault(AttendanceMark.PRESENT, 0),
                        "absent", counts.getOrDefault(AttendanceMark.ABSENT, 0), "late", counts.getOrDefault(AttendanceMark.LATE, 0),
                        "excused", counts.getOrDefault(AttendanceMark.EXCUSED, 0)));
        return sheet(edu, c, day);
    }

    /** Each student's days present, late, absent and excused in the class between {@code from} and {@code to}. */
    @Transactional(readOnly = true)
    public AttendanceSummary summary(String slug, UUID classId, LocalDate from, LocalDate to) {
        Edu edu = access.require(slug);
        SchoolClass c = visible(edu, classId);
        if (to.isBefore(from)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "The end is before the start");
        }
        List<AttendanceMark> ms = marks.findByClassIdInAndDayBetween(List.of(c.getId()), from, to);
        Set<UUID> ids = new HashSet<>();
        enrollments.findByClassId(c.getId()).forEach(e -> ids.add(e.getStudentId()));
        ms.forEach(m -> ids.add(m.getStudentId()));
        List<Student> ss = students.findByIdInAndOrgId(ids, edu.orgId()).stream()
                .sorted(Comparator.comparing(Student::getNameEn, String.CASE_INSENSITIVE_ORDER)).toList();
        List<AttendanceSummaryRow> rows = ss.stream().map(s -> new AttendanceSummaryRow(EduRefs.student(s),
                counts(ms.stream().filter(m -> m.getStudentId().equals(s.getId())).toList()))).toList();
        long days = ms.stream().map(AttendanceMark::getDay).distinct().count();
        return new AttendanceSummary(EduRefs.classRef(c), from, to, days, rows, counts(ms));
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** A student's counts over a period (every class). */
    @Transactional(readOnly = true)
    public AttendanceCounts counts(UUID studentId, LocalDate from, LocalDate to) {
        return counts(marks.findByStudentIdAndDayBetweenOrderByDayDesc(studentId, from, to));
    }

    public static AttendanceCounts counts(Collection<AttendanceMark> ms) {
        long p = 0;
        long l = 0;
        long a = 0;
        long e = 0;
        for (AttendanceMark m : ms) {
            switch (m.getStatus()) {
                case AttendanceMark.PRESENT -> p++;
                case AttendanceMark.LATE -> l++;
                case AttendanceMark.ABSENT -> a++;
                default -> e++;
            }
        }
        return new AttendanceCounts(p, l, a, e, Grading.attendanceRate(p, l, a));
    }

    /** Students in the class on {@code day} (enrolled by then and not left before it). */
    List<UUID> roster(SchoolClass c, LocalDate day) {
        return enrollments.findByClassId(c.getId()).stream()
                .filter(e -> !e.getEnrolledOn().isAfter(day) && (e.getLeftOn() == null || e.getLeftOn().isAfter(day)))
                .map(Enrollment::getStudentId).toList();
    }

    private SchoolClass visible(Edu edu, UUID classId) {
        SchoolClass c = classes.findByIdAndOrgId(classId, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.teaches(edu, c)) {
            throw BusinessException.notFound();
        }
        return c;
    }

    private AttendanceSheet sheet(Edu edu, SchoolClass c, LocalDate day) {
        List<AttendanceMark> ms = marks.findByClassIdAndDay(c.getId(), day);
        Map<UUID, AttendanceMark> byStudent = new HashMap<>();
        ms.forEach(m -> byStudent.put(m.getStudentId(), m));
        Set<UUID> ids = new HashSet<>(roster(c, day));
        ids.addAll(byStudent.keySet());
        List<Student> ss = students.findByIdInAndOrgId(ids, edu.orgId()).stream()
                .sorted(Comparator.comparing(Student::getNameEn, String.CASE_INSENSITIVE_ORDER)).toList();
        List<AttendanceRow> rows = ss.stream().map(s -> {
            AttendanceMark m = byStudent.get(s.getId());
            return new AttendanceRow(EduRefs.student(s), m == null ? null : m.getStatus(), m == null ? null : m.getNote());
        }).toList();
        AttendanceMark last = ms.stream().max(Comparator.comparing(AttendanceMark::getRecordedAt)).orElse(null);
        AcademicYear y = years.findById(c.getYearId()).orElseThrow();
        boolean canEdit = access.teaches(edu, c) && !day.isAfter(access.today()) && y.covers(day);
        return new AttendanceSheet(EduRefs.classRef(c), day, !ms.isEmpty(), rows, counts(ms), canEdit,
                last == null ? null : refs.person(last.getRecordedBy()), last == null ? null : last.getRecordedAt());
    }
}
