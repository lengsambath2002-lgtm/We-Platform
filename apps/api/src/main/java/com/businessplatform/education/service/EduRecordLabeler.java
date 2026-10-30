package com.businessplatform.education.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.RecordLabeler;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.FeeItemRepository;
import com.businessplatform.education.repository.GradeLevelRepository;
import com.businessplatform.education.repository.GuardianRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.repository.SubjectRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.education.repository.WithdrawalRepository;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/** Names and links of Education's records (students, guardians, teachers, classes, assessments, fees …) for the audit log. */
@Component
@RequiredArgsConstructor
public class EduRecordLabeler implements RecordLabeler {

    private static final Set<String> TYPES = Set.of(StudentService.RECORD, GuardianService.RECORD, TeacherService.RECORD,
            ClassService.RECORD, GradebookService.RECORD, FeeService.ITEM, FeeService.RECORD, WithdrawalService.RECORD,
            StructureService.YEAR, StructureService.GRADE, StructureService.SUBJECT, StructureService.SETTINGS);

    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final TeacherRepository teachers;
    private final UserRepository users;
    private final SchoolClassRepository classes;
    private final AssessmentRepository assessments;
    private final CourseRepository courses;
    private final SubjectRepository subjects;
    private final FeeItemRepository items;
    private final FeeInvoiceRepository invoices;
    private final WithdrawalRepository withdrawals;
    private final AcademicYearRepository years;
    private final GradeLevelRepository grades;

    @Override
    public boolean handles(String recordType) {
        return TYPES.contains(recordType);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Label> labels(OrgContext ctx, String recordType, Collection<UUID> ids) {
        Map<UUID, Label> out = new HashMap<>();
        String base = "/" + ctx.slug() + "/education";
        UUID org = ctx.orgId();
        switch (recordType) {
            case StudentService.RECORD -> students.findAllById(ids).stream().filter(s -> s.getOrgId().equals(org))
                    .forEach(s -> out.put(s.getId(), new Label(s.getNameEn() + " (" + s.getNumber() + ")", base + "/students/" + s.getId())));
            case GuardianService.RECORD -> guardians.findAllById(ids).stream().filter(g -> g.getOrgId().equals(org))
                    .forEach(g -> out.put(g.getId(), new Label(g.getName(), base + "/parents")));
            case TeacherService.RECORD -> teachers.findAllById(ids).stream().filter(t -> t.getOrgId().equals(org))
                    .forEach(t -> out.put(t.getId(), new Label(users.findById(t.getUserId()).map(u -> u.getName()).orElse(""), base + "/teachers")));
            case ClassService.RECORD -> classes.findAllById(ids).stream().filter(c -> c.getOrgId().equals(org))
                    .forEach(c -> out.put(c.getId(), new Label(c.getName(), base + "/classes/" + c.getId())));
            case GradebookService.RECORD -> assessments.findAllById(ids).stream().filter(a -> a.getOrgId().equals(org)).forEach(a -> {
                Course c = courses.findById(a.getCourseId()).orElse(null);
                String subject = c == null ? "" : subjects.findById(c.getSubjectId()).map(s -> s.getNameEn()).orElse("");
                String cls = c == null ? "" : classes.findById(c.getClassId()).map(x -> x.getName()).orElse("");
                out.put(a.getId(), new Label(a.getName() + " · " + subject + " · " + cls,
                        base + "/gradebook?course=" + a.getCourseId() + "&term=" + a.getTermId()));
            });
            case FeeService.ITEM -> items.findAllById(ids).stream().filter(f -> f.getOrgId().equals(org))
                    .forEach(f -> out.put(f.getId(), new Label(f.getNameEn(), base + "/fees?tab=items")));
            case FeeService.RECORD -> invoices.findAllById(ids).stream().filter(i -> i.getOrgId().equals(org))
                    .forEach(i -> out.put(i.getId(), new Label(i.getNumber() + " · " + students.findById(i.getStudentId())
                            .map(s -> s.getNameEn()).orElse(""), base + "/fees/invoices/" + i.getId())));
            case WithdrawalService.RECORD -> withdrawals.findAllById(ids).stream().filter(w -> w.getOrgId().equals(org))
                    .forEach(w -> out.put(w.getId(), new Label("Withdrawal · " + students.findById(w.getStudentId())
                            .map(s -> s.getNameEn()).orElse(""), base + "/students/" + w.getStudentId())));
            case StructureService.YEAR -> years.findAllById(ids).stream().filter(y -> y.getOrgId().equals(org))
                    .forEach(y -> out.put(y.getId(), new Label(y.getName(), base + "/settings")));
            case StructureService.GRADE -> grades.findAllById(ids).stream().filter(g -> g.getOrgId().equals(org))
                    .forEach(g -> out.put(g.getId(), new Label(g.getNameEn(), base + "/settings")));
            case StructureService.SUBJECT -> subjects.findAllById(ids).stream().filter(s -> s.getOrgId().equals(org))
                    .forEach(s -> out.put(s.getId(), new Label(s.getCode() + " · " + s.getNameEn(), base + "/settings")));
            default -> ids.stream().filter(id -> id.equals(org)).forEach(id -> out.put(id, new Label(ctx.orgName(), base + "/settings")));
        }
        return out;
    }
}
