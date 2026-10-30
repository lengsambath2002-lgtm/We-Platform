package com.businessplatform.education.service;

import java.math.BigDecimal;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.Activity;
import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.Guardian;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.StudentGuardian;
import com.businessplatform.education.payload.EducationPayloads.ActivityView;
import com.businessplatform.education.payload.EducationPayloads.AttendanceCounts;
import com.businessplatform.education.payload.EducationPayloads.EnrollmentView;
import com.businessplatform.education.payload.EducationPayloads.GraduateRequest;
import com.businessplatform.education.payload.EducationPayloads.GuardianLink;
import com.businessplatform.education.payload.EducationPayloads.GuardianOf;
import com.businessplatform.education.payload.EducationPayloads.PersonRef;
import com.businessplatform.education.payload.EducationPayloads.RegisterRequest;
import com.businessplatform.education.payload.EducationPayloads.StudentDetail;
import com.businessplatform.education.payload.EducationPayloads.StudentRequest;
import com.businessplatform.education.payload.EducationPayloads.StudentSummary;
import com.businessplatform.education.payload.EducationPayloads.StudentView;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.GuardianRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentGuardianRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Students: registration (numbered STU-0001, with at least one parent or guardian, optionally straight into a
 * class), their details, guardians, and leaving (graduated by an admin; withdrawn through an approved request,
 * see {@link WithdrawalService}). Admins see and change everyone; teachers see the students of their classes.
 */
@Service
@RequiredArgsConstructor
public class StudentService {

    public static final String RECORD = "edu_student";
    static final int MAX_PAGE = 100;

    private final EducationAccess access;
    private final StructureService structure;
    private final GuardianService guardianService;
    private final Enrollments enrollmentService;
    private final AttendanceService attendance;
    private final FeeService fees;
    private final WithdrawalService withdrawals;
    private final EduRefs refs;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final StudentGuardianRepository links;
    private final EnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final FeeInvoiceRepository invoices;
    private final ActivityService activity;
    private final Clock clock;

    // ── Read ─────────────────────────────────────────────────────────────────────────

    /**
     * {@code q}: name (English or Khmer) or number; {@code status}: ACTIVE, WITHDRAWN or GRADUATED; {@code classId}
     * / {@code gradeId}: in that class, or in a class of that grade this year. By name.
     */
    @Transactional(readOnly = true)
    public PageResponse<StudentSummary> list(String slug, String q, String status, UUID classId, UUID gradeId, int page, int size) {
        Edu edu = access.require(slug);
        Specification<Student> spec = (s, query, cb) -> cb.equal(s.get("orgId"), edu.orgId());
        Set<UUID> visible = access.visibleStudents(edu);
        if (visible != null) {
            if (visible.isEmpty()) {
                return new PageResponse<>(List.of(), page, size, 0);
            }
            spec = spec.and((s, query, cb) -> s.get("id").in(visible));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((s, query, cb) -> cb.equal(s.get("status"), status));
        }
        if (classId != null) {
            spec = spec.and((s, query, cb) -> {
                Subquery<UUID> in = query.subquery(UUID.class);
                Root<Enrollment> e = in.from(Enrollment.class);
                in.select(e.get("studentId")).where(cb.equal(e.get("classId"), classId));
                return s.get("id").in(in);
            });
        }
        if (gradeId != null) {
            UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
            spec = spec.and((s, query, cb) -> {
                Subquery<UUID> in = query.subquery(UUID.class);
                Root<Enrollment> e = in.from(Enrollment.class);
                Subquery<UUID> ofGrade = query.subquery(UUID.class);
                Root<SchoolClass> c = ofGrade.from(SchoolClass.class);
                ofGrade.select(c.get("id")).where(cb.equal(c.get("gradeId"), gradeId), cb.equal(c.get("yearId"), year));
                in.select(e.get("studentId")).where(e.get("classId").in(ofGrade));
                return s.get("id").in(in);
            });
        }
        if (q != null && !q.isBlank()) {
            String like = EduRefs.like(q);
            spec = spec.and((s, query, cb) -> cb.or(cb.like(cb.lower(s.get("nameEn")), like, '\\'),
                    cb.like(cb.lower(s.get("nameKm")), like, '\\'), cb.like(cb.lower(s.get("number")), like, '\\')));
        }
        Page<Student> found = students.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                Sort.by("nameEn", "number")));
        return PageResponse.of(new PageImpl<>(summaries(edu, found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public StudentDetail get(String slug, UUID id) {
        Edu edu = access.require(slug);
        return detail(edu, visible(edu, id));
    }

    /** What happened to the student, oldest first. */
    @Transactional(readOnly = true)
    public List<ActivityView> history(String slug, UUID id) {
        Edu edu = access.require(slug);
        Student s = visible(edu, id);
        List<Activity> as = activity.forRecord(edu.orgId(), RECORD, s.getId());
        Map<UUID, PersonRef> ps = refs.people(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityView(a.getId(), ps.get(a.getActorId()), a.getVerb(), a.getData(), a.getCreatedAt()))
                .toList();
    }

    // ── Change ───────────────────────────────────────────────────────────────────────

    /** Registers a student with their guardians (and a class, if given). */
    @Transactional
    public StudentDetail register(String slug, RegisterRequest in) {
        Edu edu = access.requireAdmin(slug);
        SchoolClass c = null;
        if (in.classId() != null) {
            c = classes.lock(in.classId(), edu.orgId())
                    .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "classId", "Choose a class"));
        }
        validateLinks(in.guardians());
        Student s = new Student();
        s.setId(Ids.next());
        s.setOrgId(edu.orgId());
        s.setStatus(Student.ACTIVE);
        s.setCreatedBy(edu.userId());
        s.setCreatedAt(clock.instant());
        apply(s, in.student());
        s.setNumber(structure.nextStudentNumber(edu));
        students.saveAndFlush(s);
        boolean anyPrimary = in.guardians().stream().anyMatch(GuardianLink::primary);
        for (int i = 0; i < in.guardians().size(); i++) {
            GuardianLink l = in.guardians().get(i);
            link(edu, s, l, l.primary() || (!anyPrimary && i == 0), "guardians[" + i + "]");
        }
        if (c != null) {
            enrollmentService.enroll(c, s, s.getAdmittedOn());
        }
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, s.getId(), edu.userId(), "education.student.registered",
                c == null ? Map.of("number", s.getNumber(), "name", s.getNameEn())
                        : Map.of("number", s.getNumber(), "name", s.getNameEn(), "class", c.getName()));
        return detail(edu, s);
    }

    @Transactional
    public StudentDetail update(String slug, UUID id, StudentRequest in) {
        Edu edu = access.requireAdmin(slug);
        Student s = lock(edu, id);
        apply(s, in);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, s.getId(), edu.userId(), "education.student.updated",
                Map.of("name", s.getNameEn()));
        return detail(edu, s);
    }

    /** Links a guardian (an existing one or a new one), or changes an existing link's relationship / primary flag. */
    @Transactional
    public StudentDetail addGuardian(String slug, UUID id, GuardianLink in) {
        Edu edu = access.requireAdmin(slug);
        Student s = lock(edu, id);
        validateLinks(List.of(in));
        boolean primary = in.primary() || links.findByStudentId(s.getId()).isEmpty();
        Guardian g = link(edu, s, in, primary, "guardianId");
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, s.getId(), edu.userId(), "education.student.guardian_added",
                Map.of("guardian", g.getName(), "relationship", in.relationship()));
        return detail(edu, s);
    }

    @Transactional
    public StudentDetail removeGuardian(String slug, UUID id, UUID guardianId) {
        Edu edu = access.requireAdmin(slug);
        Student s = lock(edu, id);
        List<StudentGuardian> ls = links.findByStudentId(s.getId());
        StudentGuardian l = ls.stream().filter(x -> x.getGuardianId().equals(guardianId)).findFirst()
                .orElseThrow(BusinessException::notFound);
        if (ls.size() == 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A student needs at least one parent or guardian", List.of());
        }
        links.delete(l);
        links.flush();
        if (l.isPrimary()) {
            ls.stream().filter(x -> !x.getGuardianId().equals(guardianId)).findFirst().ifPresent(x -> {
                x.setPrimary(true);
                links.save(x);
            });
        }
        String name = guardians.findById(guardianId).map(Guardian::getName).orElse("");
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, s.getId(), edu.userId(), "education.student.guardian_removed",
                Map.of("guardian", name));
        return detail(edu, s);
    }

    /** Admins: the student finished school; they leave their classes on {@code leftOn}. */
    @Transactional
    public StudentDetail graduate(String slug, UUID id, GraduateRequest in) {
        Edu edu = access.requireAdmin(slug);
        Student s = lock(edu, id);
        if (!s.active()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only an active student can graduate", List.of());
        }
        if (in.leftOn().isBefore(s.getAdmittedOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "leftOn", "This is before the admission date");
        }
        if (withdrawals.pending(s.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A withdrawal request for this student is waiting", List.of());
        }
        s.setStatus(Student.GRADUATED);
        s.setLeftOn(in.leftOn());
        s.setUpdatedAt(clock.instant());
        enrollmentService.leave(s.getId(), in.leftOn());
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, s.getId(), edu.userId(), "education.student.graduated",
                Map.of("name", s.getNameEn(), "date", in.leftOn().toString()));
        return detail(edu, s);
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** The student if the caller may see them (NOT_FOUND otherwise, so ids aren't revealed). */
    Student visible(Edu edu, UUID id) {
        Student s = students.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.sees(edu, s.getId())) {
            throw BusinessException.notFound();
        }
        return s;
    }

    private Student lock(Edu edu, UUID id) {
        return students.lock(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    /** List rows: the current year's class and grade, the main guardian, and (admins) the unpaid balance. */
    @Transactional(readOnly = true)
    public List<StudentSummary> summaries(Edu edu, Collection<Student> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = found.stream().map(Student::getId).toList();
        UUID year = structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
        Map<UUID, Enrollment> current = new HashMap<>();
        enrollments.findByStudentIdIn(ids).stream().filter(e -> e.getYearId().equals(year)).forEach(e -> current.put(e.getStudentId(), e));
        Map<UUID, SchoolClass> cs = refs.classes(edu.orgId(), current.values().stream().map(Enrollment::getClassId).toList());
        Map<UUID, GradeLevel> gs = refs.grades(edu.orgId());
        List<StudentGuardian> ls = links.findByStudentIdIn(ids);
        Map<UUID, Guardian> gu = new HashMap<>();
        guardians.findByIdInAndOrgId(ls.stream().map(StudentGuardian::getGuardianId).distinct().toList(), edu.orgId())
                .forEach(g -> gu.put(g.getId(), g));
        Map<UUID, Guardian> main = new HashMap<>();
        ls.stream().sorted(Comparator.comparing(StudentGuardian::isPrimary).reversed())
                .forEach(l -> main.putIfAbsent(l.getStudentId(), gu.get(l.getGuardianId())));
        Map<UUID, BigDecimal> balances = new HashMap<>();
        if (edu.admin()) {
            invoices.balances(edu.orgId(), ids).forEach(r -> balances.put((UUID) r[0], EduRefs.money(r[1])));
        }
        return found.stream().map(s -> {
            Enrollment e = current.get(s.getId());
            SchoolClass c = e == null ? null : cs.get(e.getClassId());
            Guardian g = main.get(s.getId());
            return new StudentSummary(s.getId(), s.getNumber(), s.getNameEn(), s.getNameKm(), s.getGender(), s.getDateOfBirth(),
                    s.getStatus(), EduRefs.classRef(c), c == null ? null : EduRefs.gradeName(gs.get(c.getGradeId())),
                    g == null ? null : g.getName(), g == null ? null : (g.getPhone() != null ? g.getPhone() : g.getEmail()),
                    edu.admin() ? balances.getOrDefault(s.getId(), BigDecimal.ZERO.setScale(2)) : null);
        }).toList();
    }

    StudentDetail detail(Edu edu, Student s) {
        List<StudentGuardian> ls = links.findByStudentId(s.getId());
        Map<UUID, Guardian> gu = new HashMap<>();
        guardians.findByIdInAndOrgId(ls.stream().map(StudentGuardian::getGuardianId).toList(), edu.orgId()).forEach(g -> gu.put(g.getId(), g));
        List<GuardianOf> gs = ls.stream().sorted(Comparator.comparing(StudentGuardian::isPrimary).reversed()).map(l -> {
            Guardian g = gu.get(l.getGuardianId());
            return new GuardianOf(g.getId(), g.getName(), g.getPhone(), g.getEmail(), g.getOccupation(), g.getAddress(),
                    l.getRelationship(), l.isPrimary());
        }).toList();
        List<Enrollment> es = enrollments.findByStudentId(s.getId());
        Map<UUID, SchoolClass> cs = refs.classes(edu.orgId(), es.stream().map(Enrollment::getClassId).toList());
        Map<UUID, GradeLevel> grades = refs.grades(edu.orgId());
        var years = refs.years(edu.orgId());
        List<EnrollmentView> history = es.stream().sorted(Comparator.comparing(Enrollment::getEnrolledOn).reversed()).map(e -> {
            SchoolClass c = cs.get(e.getClassId());
            return new EnrollmentView(EduRefs.classRef(c), years.containsKey(e.getYearId()) ? years.get(e.getYearId()).getName() : "",
                    c == null ? null : EduRefs.gradeName(grades.get(c.getGradeId())), e.getEnrolledOn(), e.getLeftOn());
        }).toList();
        AcademicYear year = structure.currentYear(edu.orgId()).orElse(null);
        Enrollment now = year == null ? null : es.stream().filter(e -> e.getYearId().equals(year.getId())).findFirst().orElse(null);
        SchoolClass current = now == null ? null : cs.get(now.getClassId());
        AttendanceCounts counts = year == null ? new AttendanceCounts(0, 0, 0, 0, null)
                : attendance.counts(s.getId(), year.getStartsOn(), year.getEndsOn());
        boolean homeroom = current != null && edu.teacherId() != null && edu.teacherId().equals(current.getHomeroomTeacherId());
        boolean pending = withdrawals.pending(s.getId());
        return new StudentDetail(view(s), gs, EduRefs.classRef(current),
                current == null ? null : EduRefs.gradeName(grades.get(current.getGradeId())), history, counts,
                edu.admin() ? fees.balance(edu, s.getId()) : null, edu.admin() ? fees.forStudent(edu, s.getId()) : null,
                withdrawals.forStudent(edu, s.getId()), edu.admin(), s.active() && !pending && (edu.admin() || homeroom),
                edu.admin() && s.active());
    }

    static StudentView view(Student s) {
        return new StudentView(s.getId(), s.getNumber(), s.getNameEn(), s.getNameKm(), s.getGender(), s.getDateOfBirth(),
                s.getAdmittedOn(), s.getAddress(), s.getMedicalNotes(), s.getStatus(), s.getLeftOn(), s.getLeaveReason(),
                s.getCreatedAt());
    }

    private void apply(Student s, StudentRequest in) {
        LocalDate today = access.today();
        if (in.dateOfBirth().isAfter(today)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dateOfBirth", "This date is in the future");
        }
        if (!in.dateOfBirth().isBefore(in.admittedOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "admittedOn", "The admission date is before the date of birth");
        }
        s.setNameEn(in.nameEn().strip());
        s.setNameKm(EduRefs.clean(in.nameKm()));
        s.setGender(in.gender());
        s.setDateOfBirth(in.dateOfBirth());
        s.setAdmittedOn(in.admittedOn());
        s.setAddress(EduRefs.clean(in.address()));
        s.setMedicalNotes(EduRefs.clean(in.medicalNotes()));
        s.setUpdatedAt(clock.instant());
    }

    private static void validateLinks(List<GuardianLink> in) {
        Set<UUID> seen = new HashSet<>();
        for (GuardianLink l : in) {
            if ((l.guardianId() == null) == (l.guardian() == null)) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "guardians", "Choose a guardian or add a new one");
            }
            if (l.guardianId() != null && !seen.add(l.guardianId())) {
                throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "guardians", "The same guardian is listed twice");
            }
        }
        if (in.stream().filter(GuardianLink::primary).count() > 1) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "guardians", "Only one guardian can be the main contact");
        }
    }

    private Guardian link(Edu edu, Student s, GuardianLink in, boolean primary, String field) {
        Guardian g = in.guardianId() != null
                ? guardians.findByIdAndOrgId(in.guardianId(), edu.orgId())
                        .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, field, "Choose a guardian or add a new one"))
                : guardianService.apply(edu, null, in.guardian());
        if (primary) {
            links.clearPrimary(s.getId());
            links.flush();
        }
        StudentGuardian l = links.findByStudentIdAndGuardianId(s.getId(), g.getId()).orElseGet(() -> {
            StudentGuardian n = new StudentGuardian();
            n.setStudentId(s.getId());
            n.setGuardianId(g.getId());
            n.setOrgId(edu.orgId());
            return n;
        });
        l.setRelationship(in.relationship());
        if (primary) {
            l.setPrimary(true);
        }
        links.saveAndFlush(l);
        return g;
    }
}
