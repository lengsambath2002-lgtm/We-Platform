package com.businessplatform.education.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.SchoolClassRepository;

import lombok.RequiredArgsConstructor;

/** Puts students in classes (one class per academic year, within the class's capacity) and takes them out. */
@Component
@RequiredArgsConstructor
public class Enrollments {

    private final EnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final AcademicYearRepository years;

    /** Enrols an active student; the class must be locked by the caller (capacity is checked against it). */
    @Transactional
    public Enrollment enroll(SchoolClass c, Student s, LocalDate on) {
        if (!s.active()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "studentIds", "Only an active student can join a class");
        }
        Optional<Enrollment> existing = enrollments.findByStudentIdAndYearId(s.getId(), c.getYearId());
        if (existing.isPresent()) {
            if (existing.get().getClassId().equals(c.getId())) {
                return existing.get();
            }
            throw BusinessException.field(ErrorCode.CONFLICT, "studentIds", "A student is already in another class this year");
        }
        if (c.getCapacity() != null && enrollments.countCurrent(c.getId()) >= c.getCapacity()) {
            throw BusinessException.field(ErrorCode.INVALID_STATE, "studentIds", "This class is full");
        }
        AcademicYear y = years.findById(c.getYearId()).orElseThrow();
        Enrollment e = new Enrollment();
        e.setClassId(c.getId());
        e.setStudentId(s.getId());
        e.setOrgId(c.getOrgId());
        e.setYearId(c.getYearId());
        e.setEnrolledOn(on.isBefore(y.getStartsOn()) ? y.getStartsOn() : on);
        return enrollments.saveAndFlush(e);
    }

    /** The student's class in {@code yearId}, if any. */
    public Optional<Enrollment> inYear(UUID studentId, UUID yearId) {
        return yearId == null ? Optional.empty() : enrollments.findByStudentIdAndYearId(studentId, yearId);
    }

    /** The student leaves every class they are still in, on {@code on}. */
    @Transactional
    public void leave(UUID studentId, LocalDate on) {
        List<Enrollment> es = enrollments.findByStudentId(studentId);
        es.stream().filter(e -> e.getLeftOn() == null).forEach(e -> e.setLeftOn(on.isBefore(e.getEnrolledOn()) ? e.getEnrolledOn() : on));
        enrollments.saveAll(es);
    }
}
