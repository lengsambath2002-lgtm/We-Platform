package com.businessplatform.education.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.CalendarService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.module.domain.Module;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.service.OrgAccess;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.org.service.RoleCatalogue;

import lombok.RequiredArgsConstructor;

/**
 * Who may do what in Education, checked on every request. A member of the organisation (NOT_FOUND otherwise)
 * with the module switched on (MODULE_DISABLED), never a guest, and either an Owner / Admin or a member with an
 * active teacher profile (FORBIDDEN otherwise).
 * <ul>
 * <li><b>Owners and Admins</b> ({@code education.manage}): everything — academic structure and settings, teachers,
 * students and guardians, classes, courses and the timetable, every class's attendance and gradebook, fees, and
 * deciding withdrawals (default workflow "Student withdrawal": an Admin).</li>
 * <li><b>Teachers</b> ({@code education.teach}): the classes they teach — as homeroom teacher or through a course —
 * and the students in them (read only, with their guardians' contacts); attendance of those classes; assessments
 * and scores of their own courses; results and report cards of their students; the timetable of their classes;
 * a withdrawal request for a student of their homeroom class. No fees.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class EducationAccess {

    /** How Education records appear in the activity log and the approval engine. */
    public static final String MODULE = Module.EDUCATION;

    private final OrgAccess access;
    private final ModuleService modules;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final Clock clock;

    /** The caller in Education: their organisation context and, if they teach, their teacher profile id. */
    public record Edu(OrgContext ctx, UUID teacherId) {

        public boolean admin() {
            return ctx.can(RoleCatalogue.EDUCATION);
        }

        public UUID orgId() {
            return ctx.orgId();
        }

        public UUID userId() {
            return ctx.userId();
        }

        public String slug() {
            return ctx.slug();
        }
    }

    @Transactional(readOnly = true)
    public Edu require(String slug) {
        OrgContext ctx = access.require(slug);
        modules.require(ctx.orgId(), MODULE);
        return of(ctx).orElseThrow(BusinessException::forbidden);
    }

    public Edu requireAdmin(String slug) {
        Edu edu = require(slug);
        if (!edu.admin()) {
            throw BusinessException.forbidden();
        }
        return edu;
    }

    /** The caller's Education role, or empty (a guest, or a member who neither runs the school nor teaches). */
    @Transactional(readOnly = true)
    public Optional<Edu> of(OrgContext ctx) {
        if (ctx.isGuest()) {
            return Optional.empty();
        }
        UUID teacher = teachers.findByOrgIdAndUserId(ctx.orgId(), ctx.userId()).filter(Teacher::isActive).map(Teacher::getId)
                .orElse(null);
        if (!ctx.can(RoleCatalogue.EDUCATION) && teacher == null) {
            return Optional.empty();
        }
        return Optional.of(new Edu(ctx, teacher));
    }

    /** For the core's hooks, which must not throw: the role when the module is on, else empty. */
    @Transactional(readOnly = true)
    public Optional<Edu> usable(OrgContext ctx) {
        if (!modules.enabled(ctx.orgId()).contains(MODULE)) {
            return Optional.empty();
        }
        return of(ctx);
    }

    // ── Teaching scope ───────────────────────────────────────────────────────────────

    /** The classes {@code teacherId} teaches: as homeroom teacher or through a course (any year). */
    @Transactional(readOnly = true)
    public Set<UUID> classesTaughtBy(UUID teacherId) {
        Set<UUID> out = new HashSet<>();
        if (teacherId == null) {
            return out;
        }
        classes.findByHomeroomTeacherId(teacherId).forEach(c -> out.add(c.getId()));
        courses.findByTeacherId(teacherId).forEach(c -> out.add(c.getClassId()));
        return out;
    }

    /** Admins, the class's homeroom teacher, or a teacher of one of its courses. */
    public boolean teaches(Edu edu, SchoolClass c) {
        if (edu.admin()) {
            return true;
        }
        if (edu.teacherId() == null) {
            return false;
        }
        return edu.teacherId().equals(c.getHomeroomTeacherId())
                || courses.findByClassId(c.getId()).stream().anyMatch(x -> edu.teacherId().equals(x.getTeacherId()));
    }

    /** Admins, or the course's teacher. */
    public static boolean teaches(Edu edu, Course c) {
        return edu.admin() || (edu.teacherId() != null && edu.teacherId().equals(c.getTeacherId()));
    }

    /** Admins, or a teacher of a class the student is (or was) in. */
    @Transactional(readOnly = true)
    public boolean sees(Edu edu, UUID studentId) {
        if (edu.admin()) {
            return true;
        }
        Set<UUID> mine = classesTaughtBy(edu.teacherId());
        return enrollments.findByStudentId(studentId).stream().anyMatch(e -> mine.contains(e.getClassId()));
    }

    /** The students a teacher sees (those in the classes they teach); null for admins (everyone). */
    @Transactional(readOnly = true)
    public Set<UUID> visibleStudents(Edu edu) {
        if (edu.admin()) {
            return null;
        }
        Set<UUID> out = new HashSet<>();
        Set<UUID> mine = classesTaughtBy(edu.teacherId());
        if (!mine.isEmpty()) {
            enrollments.findByClassIdIn(mine).forEach(e -> out.add(e.getStudentId()));
        }
        return out;
    }

    public static void requireAdmin(Edu edu) {
        if (!edu.admin()) {
            throw BusinessException.forbidden();
        }
    }

    /** Today in the organisation's time zone. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(CalendarService.ZONE));
    }
}
