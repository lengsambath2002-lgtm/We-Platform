package com.businessplatform.education.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.Subject;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.domain.Term;
import com.businessplatform.education.payload.EducationPayloads.ClassRef;
import com.businessplatform.education.payload.EducationPayloads.CourseRef;
import com.businessplatform.education.payload.EducationPayloads.PersonRef;
import com.businessplatform.education.payload.EducationPayloads.StudentRef;
import com.businessplatform.education.payload.EducationPayloads.SubjectView;
import com.businessplatform.education.payload.EducationPayloads.TeacherRef;
import com.businessplatform.education.payload.EducationPayloads.TermView;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.GradeLevelRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.SubjectRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Names and references used on Education's views (people, teachers, classes, subjects, grades, years). */
@Component
@RequiredArgsConstructor
public class EduRefs {

    private final UserRepository users;
    private final TeacherRepository teachers;
    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final GradeLevelRepository grades;
    private final AcademicYearRepository years;

    @Transactional(readOnly = true)
    public Map<UUID, AppUser> users(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return new HashMap<>();
        }
        return users.findByIdIn(wanted).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, PersonRef> people(Collection<UUID> ids) {
        Map<UUID, PersonRef> out = new HashMap<>();
        users(ids).values().forEach(u -> out.put(u.getId(), new PersonRef(u.getId(), u.getName(), u.getAvatarUrl())));
        return out;
    }

    public PersonRef person(UUID id) {
        return id == null ? null : people(List.of(id)).get(id);
    }

    /** Teacher references (with their names) for the given teacher ids. */
    @Transactional(readOnly = true)
    public Map<UUID, TeacherRef> teachers(Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<UUID, TeacherRef> out = new HashMap<>();
        if (wanted.isEmpty()) {
            return out;
        }
        List<Teacher> found = teachers.findAllById(wanted);
        Map<UUID, AppUser> us = users(found.stream().map(Teacher::getUserId).toList());
        found.forEach(t -> {
            AppUser u = us.get(t.getUserId());
            out.put(t.getId(), new TeacherRef(t.getId(), t.getUserId(), u == null ? "" : u.getName(), u == null ? null : u.getAvatarUrl()));
        });
        return out;
    }

    public TeacherRef teacher(UUID id) {
        return id == null ? null : teachers(List.of(id)).get(id);
    }

    @Transactional(readOnly = true)
    public Map<UUID, SchoolClass> classes(UUID orgId, Collection<UUID> ids) {
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return new HashMap<>();
        }
        return classes.findByIdInAndOrgId(wanted, orgId).stream().collect(Collectors.toMap(SchoolClass::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, Subject> subjects(UUID orgId) {
        return subjects.findByOrgIdOrderByNameEn(orgId).stream().collect(Collectors.toMap(Subject::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, GradeLevel> grades(UUID orgId) {
        return grades.findByOrgIdOrderByPositionAscNameEnAsc(orgId).stream()
                .collect(Collectors.toMap(GradeLevel::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<UUID, AcademicYear> years(UUID orgId) {
        return years.findByOrgIdOrderByStartsOnDesc(orgId).stream().collect(Collectors.toMap(AcademicYear::getId, Function.identity()));
    }

    /** Course references (class, subject, teacher) for the given courses. */
    @Transactional(readOnly = true)
    public Map<UUID, CourseRef> courses(UUID orgId, Collection<Course> found) {
        Map<UUID, SchoolClass> cs = classes(orgId, found.stream().map(Course::getClassId).toList());
        Map<UUID, Subject> ss = subjects(orgId);
        Map<UUID, TeacherRef> ts = teachers(found.stream().map(Course::getTeacherId).toList());
        Map<UUID, CourseRef> out = new HashMap<>();
        found.forEach(c -> out.put(c.getId(), new CourseRef(c.getId(), classRef(cs.get(c.getClassId())),
                subject(ss.get(c.getSubjectId())), ts.get(c.getTeacherId()))));
        return out;
    }

    public static ClassRef classRef(SchoolClass c) {
        return c == null ? null : new ClassRef(c.getId(), c.getName(), c.getYearId());
    }

    public static StudentRef student(Student s) {
        return s == null ? null : new StudentRef(s.getId(), s.getNumber(), s.getNameEn(), s.getNameKm(), s.getStatus());
    }

    public static SubjectView subject(Subject s) {
        return s == null ? null : new SubjectView(s.getId(), s.getCode(), s.getNameEn(), s.getNameKm(), s.isActive());
    }

    public static TermView term(Term t) {
        return t == null ? null : new TermView(t.getId(), t.getYearId(), t.getName(), t.getStartsOn(), t.getEndsOn());
    }

    public static String gradeName(GradeLevel g) {
        return g == null ? null : g.getNameEn();
    }

    /** Trimmed text, or null when empty. */
    public static String clean(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** A JPQL sum or count as money. */
    public static BigDecimal money(Object v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : new BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** Escapes a search for LIKE (with {@code \\} as the escape) and wraps it in %…%, lower-cased. */
    public static String like(String q) {
        return "%" + q.strip().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
