package com.businessplatform.education.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.AttendanceMark;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.FeeInvoice;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.Grading;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Term;
import com.businessplatform.education.payload.EducationPayloads.AttendanceByClass;
import com.businessplatform.education.payload.EducationPayloads.FeesByGrade;
import com.businessplatform.education.payload.EducationPayloads.Reports;
import com.businessplatform.education.payload.EducationPayloads.ResultsByClass;
import com.businessplatform.education.repository.AttendanceRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * Education reports for the current academic year's classes (a teacher's own classes): attendance by class over
 * a period, results by class for a term (average of term averages, pass rate against the pass mark), and — for
 * admins — fees by grade level (billed, collected, outstanding, overdue; void invoices excluded).
 */
@Service
@RequiredArgsConstructor
public class EduReportService {

    private final EducationAccess access;
    private final StructureService structure;
    private final GradebookService gradebook;
    private final EduRefs refs;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final AttendanceRepository marks;
    private final FeeInvoiceRepository invoices;

    /** {@code from}/{@code to}: the attendance period (default: the current term, else the last 30 days); {@code termId}: results (default: the current term). */
    @Transactional
    public Reports reports(String slug, LocalDate from, LocalDate to, UUID termId) {
        Edu edu = access.require(slug);
        AcademicYear year = structure.currentYear(edu.orgId()).orElse(null);
        Term term = termId != null ? structure.term(edu, termId) : year == null ? null : structure.termNow(year.getId()).orElse(null);
        LocalDate today = access.today();
        LocalDate end = to != null ? to : term != null && term.getEndsOn().isBefore(today) ? term.getEndsOn() : today;
        LocalDate start = from != null ? from : term != null ? term.getStartsOn() : end.minusDays(29);
        if (end.isBefore(start)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "to", "The end is before the start");
        }
        Set<UUID> mine = access.classesTaughtBy(edu.teacherId());
        List<SchoolClass> scope = year == null ? List.of() : classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year.getId()).stream()
                .filter(c -> edu.admin() || mine.contains(c.getId())).toList();
        Map<UUID, GradeLevel> grades = refs.grades(edu.orgId());
        scope = scope.stream().sorted(java.util.Comparator.comparing((SchoolClass c) -> grades.containsKey(c.getGradeId())
                ? grades.get(c.getGradeId()).getPosition() : 0).thenComparing(SchoolClass::getName)).toList();

        List<AttendanceMark> ms = scope.isEmpty() ? List.of()
                : marks.findByClassIdInAndDayBetween(scope.stream().map(SchoolClass::getId).toList(), start, end);
        List<AttendanceByClass> attendance = scope.stream().map(c -> {
            List<AttendanceMark> of = ms.stream().filter(m -> m.getClassId().equals(c.getId())).toList();
            return new AttendanceByClass(EduRefs.classRef(c), EduRefs.gradeName(grades.get(c.getGradeId())),
                    of.stream().map(AttendanceMark::getDay).distinct().count(), AttendanceService.counts(of));
        }).toList();

        List<ResultsByClass> results = null;
        if (term != null) {
            BigDecimal pass = structure.settings(edu).getPassMark();
            Term t = term;
            results = scope.stream().map(c -> {
                var r = gradebook.results(edu, c, t);
                List<BigDecimal> avgs = r.averages().values().stream().filter(Objects::nonNull).toList();
                long passed = avgs.stream().filter(a -> a.compareTo(pass) >= 0).count();
                BigDecimal rate = avgs.isEmpty() ? null
                        : Grading.round(new BigDecimal(passed * 100).divide(new BigDecimal(avgs.size()), MathContext.DECIMAL128));
                return new ResultsByClass(EduRefs.classRef(c), EduRefs.gradeName(grades.get(c.getGradeId())), r.students().size(),
                        avgs.size(), Grading.average(avgs), rate);
            }).toList();
        }

        List<FeesByGrade> fees = null;
        if (edu.admin()) {
            List<FeeInvoice> billed = invoices.billed(edu.orgId());
            Map<UUID, UUID> gradeOf = new HashMap<>();
            if (year != null && !billed.isEmpty()) {
                Map<UUID, SchoolClass> yearClasses = new HashMap<>();
                classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), year.getId()).forEach(c -> yearClasses.put(c.getId(), c));
                for (Enrollment e : enrollments.findByStudentIdIn(billed.stream().map(FeeInvoice::getStudentId).distinct().toList())) {
                    SchoolClass c = yearClasses.get(e.getClassId());
                    if (c != null) {
                        gradeOf.put(e.getStudentId(), c.getGradeId());
                    }
                }
            }
            Map<UUID, List<FeeInvoice>> byGrade = new LinkedHashMap<>();
            grades.values().stream().sorted(java.util.Comparator.comparing(GradeLevel::getPosition)).forEach(g -> byGrade.put(g.getId(), new ArrayList<>()));
            List<FeeInvoice> none = new ArrayList<>();
            billed.forEach(i -> {
                UUID g = gradeOf.get(i.getStudentId());
                (g != null && byGrade.containsKey(g) ? byGrade.get(g) : none).add(i);
            });
            fees = new ArrayList<>();
            for (var e : byGrade.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    fees.add(feeRow(e.getKey(), grades.get(e.getKey()).getNameEn(), e.getValue(), today));
                }
            }
            if (!none.isEmpty()) {
                fees.add(feeRow(null, null, none, today));
            }
        }
        return new Reports(start, end, EduRefs.term(term), attendance, results, fees);
    }

    private static FeesByGrade feeRow(UUID gradeId, String name, List<FeeInvoice> is, LocalDate today) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return new FeesByGrade(gradeId, name, is.size(), is.stream().map(FeeInvoice::getAmount).reduce(zero, BigDecimal::add),
                is.stream().map(FeeInvoice::getPaid).reduce(zero, BigDecimal::add), is.stream().map(FeeInvoice::balance).reduce(zero, BigDecimal::add),
                is.stream().filter(i -> i.overdue(today)).map(FeeInvoice::balance).reduce(zero, BigDecimal::add));
    }
}
