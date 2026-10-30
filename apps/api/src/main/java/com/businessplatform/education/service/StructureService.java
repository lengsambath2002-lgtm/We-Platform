package com.businessplatform.education.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.EduSettings;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.Subject;
import com.businessplatform.education.domain.Term;
import com.businessplatform.education.payload.EducationPayloads.GradeRequest;
import com.businessplatform.education.payload.EducationPayloads.GradeView;
import com.businessplatform.education.payload.EducationPayloads.SettingsRequest;
import com.businessplatform.education.payload.EducationPayloads.SettingsView;
import com.businessplatform.education.payload.EducationPayloads.SubjectRequest;
import com.businessplatform.education.payload.EducationPayloads.SubjectView;
import com.businessplatform.education.payload.EducationPayloads.TermRequest;
import com.businessplatform.education.payload.EducationPayloads.TermView;
import com.businessplatform.education.payload.EducationPayloads.YearRequest;
import com.businessplatform.education.payload.EducationPayloads.YearView;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.EduSettingsRepository;
import com.businessplatform.education.repository.GradeLevelRepository;
import com.businessplatform.education.repository.SubjectRepository;
import com.businessplatform.education.repository.TermRepository;
import com.businessplatform.education.service.EducationAccess.Edu;

import lombok.RequiredArgsConstructor;

/**
 * The school's academic structure: settings (name, principal, pass mark), academic years (one current) with their
 * terms, grade levels and subjects. Admins change them; teachers read them. The first use adds editable default
 * grade levels (Grade 1–12) and the subjects of the Cambodian curriculum. Numbers: STU-0001 for students,
 * FEE-0001 for fee invoices, counted per organisation.
 */
@Service
@RequiredArgsConstructor
public class StructureService {

    public static final String SETTINGS = "edu_settings";
    public static final String YEAR = "edu_year";
    public static final String GRADE = "edu_grade";
    public static final String SUBJECT = "edu_subject";

    private final EducationAccess access;
    private final EduSettingsRepository settings;
    private final AcademicYearRepository years;
    private final TermRepository terms;
    private final GradeLevelRepository grades;
    private final SubjectRepository subjects;
    private final AssessmentRepository assessments;
    private final ActivityService activity;
    private final Clock clock;

    // ── Settings ─────────────────────────────────────────────────────────────────────

    @Transactional
    public SettingsView getSettings(String slug) {
        Edu edu = access.require(slug);
        return view(edu, settings(edu));
    }

    @Transactional
    public SettingsView updateSettings(String slug, SettingsRequest in) {
        Edu edu = access.requireAdmin(slug);
        EduSettings s = settings(edu);
        s.setSchoolName(EduRefs.clean(in.schoolName()));
        s.setPrincipalName(EduRefs.clean(in.principalName()));
        s.setAddress(EduRefs.clean(in.address()));
        s.setPhone(EduRefs.clean(in.phone()));
        s.setPassMark(in.passMark());
        s.setUpdatedBy(edu.userId());
        s.setUpdatedAt(clock.instant());
        activity.record(edu.orgId(), EducationAccess.MODULE, SETTINGS, edu.orgId(), edu.userId(), "education.settings.updated",
                Map.of("passMark", s.getPassMark().toPlainString()));
        return view(edu, s);
    }

    @Transactional
    public EduSettings settings(Edu edu) {
        settings.ensure(edu.orgId());
        return settings.findById(edu.orgId()).orElseThrow();
    }

    static SettingsView view(Edu edu, EduSettings s) {
        return new SettingsView(s.getSchoolName(), s.getPrincipalName(), s.getAddress(), s.getPhone(), s.getPassMark(),
                edu.ctx().orgName());
    }

    /** The next student number, "STU-0001". */
    @Transactional
    public String nextStudentNumber(Edu edu) {
        return String.format("STU-%04d", settings.next(edu.orgId(), "STUDENT"));
    }

    /** The next fee invoice number, "FEE-0001". */
    @Transactional
    public String nextInvoiceNumber(Edu edu) {
        return String.format("FEE-%04d", settings.next(edu.orgId(), "INVOICE"));
    }

    // ── Academic years and terms ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<YearView> years(String slug) {
        Edu edu = access.require(slug);
        return years.findByOrgIdOrderByStartsOnDesc(edu.orgId()).stream().map(this::view).toList();
    }

    @Transactional
    public YearView saveYear(String slug, UUID id, YearRequest in) {
        Edu edu = access.requireAdmin(slug);
        AcademicYear y;
        if (id == null) {
            y = new AcademicYear();
            y.setId(Ids.next());
            y.setOrgId(edu.orgId());
            y.setCreatedAt(clock.instant());
        } else {
            y = year(edu, id);
        }
        String name = in.name().strip();
        if (!in.endsOn().isAfter(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "The end is before the start");
        }
        if (years.existsByOrgIdAndNameIgnoreCaseAndIdNot(edu.orgId(), name, y.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another academic year has this name");
        }
        if (id != null) {
            for (Term t : terms.findByYearIdOrderByStartsOn(y.getId())) {
                if (t.getStartsOn().isBefore(in.startsOn()) || t.getEndsOn().isAfter(in.endsOn())) {
                    throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "startsOn", "A term of this year falls outside these dates");
                }
            }
        }
        y.setName(name);
        y.setStartsOn(in.startsOn());
        y.setEndsOn(in.endsOn());
        boolean makeCurrent = in.current() || (id == null && years.findByOrgIdAndCurrentTrue(edu.orgId()).isEmpty());
        if (makeCurrent && !y.isCurrent()) {
            years.clearCurrent(edu.orgId());
            years.flush();
            y.setCurrent(true);
        }
        years.saveAndFlush(y);
        activity.record(edu.orgId(), EducationAccess.MODULE, YEAR, y.getId(), edu.userId(),
                id == null ? "education.year.created" : "education.year.updated", Map.of("name", y.getName()));
        return view(y);
    }

    @Transactional
    public TermView saveTerm(String slug, UUID yearId, UUID termId, TermRequest in) {
        Edu edu = access.requireAdmin(slug);
        Term t;
        AcademicYear y;
        if (termId == null) {
            y = year(edu, yearId);
            t = new Term();
            t.setId(Ids.next());
            t.setOrgId(edu.orgId());
            t.setYearId(y.getId());
        } else {
            t = term(edu, termId);
            y = year(edu, t.getYearId());
        }
        if (in.endsOn().isBefore(in.startsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "endsOn", "The end is before the start");
        }
        if (!y.covers(in.startsOn()) || !y.covers(in.endsOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "startsOn", "A term must fall inside its academic year");
        }
        String name = in.name().strip();
        if (terms.existsByYearIdAndNameIgnoreCaseAndIdNot(y.getId(), name, t.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "name", "Another term of this year has this name");
        }
        boolean overlaps = terms.findByYearIdOrderByStartsOn(y.getId()).stream().filter(o -> !o.getId().equals(t.getId()))
                .anyMatch(o -> !o.getStartsOn().isAfter(in.endsOn()) && !in.startsOn().isAfter(o.getEndsOn()));
        if (overlaps) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "startsOn", "Terms of a year can't overlap");
        }
        t.setName(name);
        t.setStartsOn(in.startsOn());
        t.setEndsOn(in.endsOn());
        terms.save(t);
        activity.record(edu.orgId(), EducationAccess.MODULE, YEAR, y.getId(), edu.userId(),
                termId == null ? "education.term.created" : "education.term.updated", Map.of("name", t.getName(), "year", y.getName()));
        return EduRefs.term(t);
    }

    @Transactional
    public void deleteTerm(String slug, UUID termId) {
        Edu edu = access.requireAdmin(slug);
        Term t = term(edu, termId);
        if (assessments.existsByTermId(t.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This term has assessments; it can't be deleted", List.of());
        }
        AcademicYear y = year(edu, t.getYearId());
        terms.delete(t);
        activity.record(edu.orgId(), EducationAccess.MODULE, YEAR, y.getId(), edu.userId(), "education.term.deleted",
                Map.of("name", t.getName(), "year", y.getName()));
    }

    public AcademicYear year(Edu edu, UUID id) {
        return years.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    public Term term(Edu edu, UUID id) {
        return terms.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    public Optional<AcademicYear> currentYear(UUID orgId) {
        return years.findByOrgIdAndCurrentTrue(orgId);
    }

    /** The current year, or a refusal that says to set one up. */
    public AcademicYear requireCurrentYear(Edu edu) {
        return currentYear(edu.orgId()).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE,
                "Set up the current academic year first", List.of()));
    }

    /** The term of {@code yearId} that covers today, else its latest one that started, else its first. */
    public Optional<Term> termNow(UUID yearId) {
        List<Term> ts = terms.findByYearIdOrderByStartsOn(yearId);
        var today = access.today();
        return ts.stream().filter(t -> t.covers(today)).findFirst()
                .or(() -> ts.stream().filter(t -> !t.getStartsOn().isAfter(today)).reduce((a, b) -> b))
                .or(() -> ts.stream().findFirst());
    }

    YearView view(AcademicYear y) {
        return new YearView(y.getId(), y.getName(), y.getStartsOn(), y.getEndsOn(), y.isCurrent(),
                terms.findByYearIdOrderByStartsOn(y.getId()).stream().map(EduRefs::term).toList());
    }

    // ── Grade levels ─────────────────────────────────────────────────────────────────

    /** Active grade levels; admins also see switched-off ones. */
    @Transactional
    public List<GradeView> grades(String slug) {
        Edu edu = access.require(slug);
        return allGrades(edu).stream().filter(g -> g.isActive() || edu.admin()).map(StructureService::view).toList();
    }

    @Transactional
    public GradeView saveGrade(String slug, UUID id, GradeRequest in) {
        Edu edu = access.requireAdmin(slug);
        GradeLevel g;
        if (id == null) {
            g = new GradeLevel();
            g.setId(Ids.next());
            g.setOrgId(edu.orgId());
        } else {
            g = grade(edu, id);
        }
        String name = in.nameEn().strip();
        if (grades.existsByOrgIdAndNameEnIgnoreCaseAndIdNot(edu.orgId(), name, g.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "nameEn", "Another grade level has this name");
        }
        g.setNameEn(name);
        g.setNameKm(EduRefs.clean(in.nameKm()));
        g.setPosition(in.position().shortValue());
        g.setActive(in.active() == null || in.active());
        grades.save(g);
        activity.record(edu.orgId(), EducationAccess.MODULE, GRADE, g.getId(), edu.userId(),
                id == null ? "education.grade.created" : "education.grade.updated", Map.of("name", g.getNameEn()));
        return view(g);
    }

    @Transactional
    public List<GradeLevel> allGrades(Edu edu) {
        if (grades.countByOrgId(edu.orgId()) == 0) {
            String[] km = { "១", "២", "៣", "៤", "៥", "៦", "៧", "៨", "៩", "១០", "១១", "១២" };
            for (int i = 1; i <= 12; i++) {
                grades.seed(edu.orgId(), "Grade " + i, "ថ្នាក់ទី" + km[i - 1], (short) i);
            }
        }
        return grades.findByOrgIdOrderByPositionAscNameEnAsc(edu.orgId());
    }

    public GradeLevel grade(Edu edu, UUID id) {
        return grades.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    static GradeView view(GradeLevel g) {
        return new GradeView(g.getId(), g.getNameEn(), g.getNameKm(), g.getPosition(), g.isActive());
    }

    // ── Subjects ─────────────────────────────────────────────────────────────────────

    @Transactional
    public List<SubjectView> subjects(String slug) {
        Edu edu = access.require(slug);
        return allSubjects(edu).stream().filter(s -> s.isActive() || edu.admin()).map(EduRefs::subject).toList();
    }

    @Transactional
    public SubjectView saveSubject(String slug, UUID id, SubjectRequest in) {
        Edu edu = access.requireAdmin(slug);
        Subject s;
        if (id == null) {
            s = new Subject();
            s.setId(Ids.next());
            s.setOrgId(edu.orgId());
        } else {
            s = subject(edu, id);
        }
        String code = in.code().strip().toUpperCase();
        if (subjects.existsByOrgIdAndCodeIgnoreCaseAndIdNot(edu.orgId(), code, s.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "code", "Another subject has this code");
        }
        s.setCode(code);
        s.setNameEn(in.nameEn().strip());
        s.setNameKm(EduRefs.clean(in.nameKm()));
        s.setActive(in.active() == null || in.active());
        subjects.save(s);
        activity.record(edu.orgId(), EducationAccess.MODULE, SUBJECT, s.getId(), edu.userId(),
                id == null ? "education.subject.created" : "education.subject.updated", Map.of("name", s.getNameEn(), "code", code));
        return EduRefs.subject(s);
    }

    @Transactional
    public List<Subject> allSubjects(Edu edu) {
        if (subjects.countByOrgId(edu.orgId()) == 0) {
            subjects.seed(edu.orgId(), "KHM", "Khmer", "ភាសាខ្មែរ");
            subjects.seed(edu.orgId(), "MATH", "Mathematics", "គណិតវិទ្យា");
            subjects.seed(edu.orgId(), "PHYS", "Physics", "រូបវិទ្យា");
            subjects.seed(edu.orgId(), "CHEM", "Chemistry", "គីមីវិទ្យា");
            subjects.seed(edu.orgId(), "BIO", "Biology", "ជីវវិទ្យា");
            subjects.seed(edu.orgId(), "ES", "Earth science", "ផែនដីវិទ្យា");
            subjects.seed(edu.orgId(), "HIST", "History", "ប្រវត្តិវិទ្យា");
            subjects.seed(edu.orgId(), "GEO", "Geography", "ភូមិវិទ្យា");
            subjects.seed(edu.orgId(), "CIV", "Morality and civics", "សីលធម៌ ពលរដ្ឋវិទ្យា");
            subjects.seed(edu.orgId(), "ENG", "English", "ភាសាអង់គ្លេស");
            subjects.seed(edu.orgId(), "ICT", "ICT", "បច្ចេកវិទ្យាព័ត៌មាន");
            subjects.seed(edu.orgId(), "PE", "Physical education", "អប់រំកាយ");
        }
        return subjects.findByOrgIdOrderByNameEn(edu.orgId());
    }

    public Subject subject(Edu edu, UUID id) {
        return subjects.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }
}
