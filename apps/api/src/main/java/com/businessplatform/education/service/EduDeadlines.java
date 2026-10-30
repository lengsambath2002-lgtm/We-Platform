package com.businessplatform.education.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.calendar.DeadlineSource;
import com.businessplatform.education.domain.Assessment;
import com.businessplatform.education.domain.Course;
import com.businessplatform.education.domain.FeeInvoice;
import com.businessplatform.education.domain.FeeItem;
import com.businessplatform.education.repository.AssessmentRepository;
import com.businessplatform.education.repository.CourseRepository;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.FeeItemRepository;
import com.businessplatform.education.repository.ScoreRepository;
import com.businessplatform.org.service.OrgContext;

import lombok.RequiredArgsConstructor;

/**
 * Education on the calendar: fee due dates for admins (done once everything billed is paid; left out of "only
 * mine"), and assessments for the course's teacher and admins (done once every student has a score; "only mine"
 * keeps the caller's own courses). Nothing when the module is off or the person has no Education role.
 */
@Component
@RequiredArgsConstructor
public class EduDeadlines implements DeadlineSource {

    private final EducationAccess access;
    private final FeeItemRepository items;
    private final FeeInvoiceRepository invoices;
    private final AssessmentRepository assessments;
    private final CourseRepository courses;
    private final ScoreRepository scores;
    private final EnrollmentRepository enrollments;
    private final EduRefs refs;

    @Override
    @Transactional(readOnly = true)
    public List<Deadline> deadlines(OrgContext ctx, LocalDate from, LocalDate to, boolean mineOnly) {
        var role = access.usable(ctx);
        if (role.isEmpty()) {
            return List.of();
        }
        var edu = role.get();
        String base = "/" + ctx.slug() + "/education";
        List<Deadline> out = new ArrayList<>();
        if (edu.admin() && !mineOnly) {
            List<FeeItem> due = items.dueBetween(ctx.orgId(), from, to).stream().filter(FeeItem::isActive).toList();
            List<FeeInvoice> billed = due.isEmpty() ? List.of() : invoices.findByFeeItemIdIn(due.stream().map(FeeItem::getId).toList());
            var grades = refs.grades(ctx.orgId());
            for (FeeItem f : due) {
                List<FeeInvoice> mine = billed.stream().filter(i -> f.getId().equals(i.getFeeItemId()) && !FeeInvoice.VOID.equals(i.getStatus())).toList();
                boolean done = !mine.isEmpty() && mine.stream().allMatch(i -> FeeInvoice.PAID.equals(i.getStatus()));
                String grade = f.getGradeId() == null || !grades.containsKey(f.getGradeId()) ? null : grades.get(f.getGradeId()).getNameEn();
                out.add(new Deadline(EducationAccess.MODULE, f.getId(), "FEE", grade == null ? f.getNameEn() : f.getNameEn() + " · " + grade,
                        f.getDueOn(), done ? "DONE" : "TODO", base + "/fees?tab=items", false));
            }
        }
        List<Assessment> held = assessments.findByOrgIdAndHeldOnBetweenOrderByHeldOn(ctx.orgId(), from, to);
        if (!held.isEmpty()) {
            Map<UUID, Course> cs = new HashMap<>();
            courses.findAllById(held.stream().map(Assessment::getCourseId).distinct().toList()).forEach(c -> cs.put(c.getId(), c));
            var cr = refs.courses(ctx.orgId(), cs.values());
            Map<UUID, Long> graded = new HashMap<>();
            scores.findByAssessmentIdIn(held.stream().map(Assessment::getId).toList()).forEach(s -> graded.merge(s.getAssessmentId(), 1L, Long::sum));
            for (Assessment a : held) {
                Course c = cs.get(a.getCourseId());
                boolean mine = edu.teacherId() != null && edu.teacherId().equals(c.getTeacherId());
                if (!(edu.admin() || mine) || (mineOnly && !mine)) {
                    continue;
                }
                var ref = cr.get(c.getId());
                long size = enrollments.countCurrent(c.getClassId());
                boolean done = size > 0 && graded.getOrDefault(a.getId(), 0L) >= size;
                out.add(new Deadline(EducationAccess.MODULE, a.getId(), ref.classRef() == null ? "" : ref.classRef().name(),
                        a.getName() + " · " + (ref.subject() == null ? "" : ref.subject().nameEn()), a.getHeldOn(), done ? "DONE" : "TODO",
                        base + "/gradebook?course=" + c.getId() + "&term=" + a.getTermId(), mine));
            }
        }
        return out;
    }

    @Override
    public boolean canSeeProject(OrgContext ctx, UUID projectId) {
        return false;
    }
}
