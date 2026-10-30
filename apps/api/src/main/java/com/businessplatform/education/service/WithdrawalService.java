package com.businessplatform.education.service;

import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.activity.ActivityService;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.domain.Teacher;
import com.businessplatform.education.domain.Withdrawal;
import com.businessplatform.education.payload.EducationPayloads.PersonRef;
import com.businessplatform.education.payload.EducationPayloads.WithdrawalRequest;
import com.businessplatform.education.payload.EducationPayloads.WithdrawalView;
import com.businessplatform.education.repository.AcademicYearRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.repository.TeacherRepository;
import com.businessplatform.education.repository.WithdrawalRepository;
import com.businessplatform.education.service.EducationAccess.Edu;
import com.businessplatform.notification.NotificationService;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.service.OrgContext;
import com.businessplatform.workflow.payload.WorkflowPayloads.ApprovalDetail;
import com.businessplatform.workflow.payload.WorkflowPayloads.StepInput;
import com.businessplatform.workflow.payload.WorkflowPayloads.SubmitRequest;
import com.businessplatform.workflow.service.ApprovalClosed;
import com.businessplatform.workflow.service.ApprovalService;
import com.businessplatform.workflow.service.WorkflowService;

import lombok.RequiredArgsConstructor;

/**
 * Withdrawing a student goes through the approval engine. The student's homeroom teacher or an admin asks
 * (a date and a reason); the request follows the organisation's education workflow — by default "Student
 * withdrawal", one step decided by an Admin (created on first use; Admins may change it in Admin → Workflows).
 * Approved ({@link ApprovalClosed}): the student becomes WITHDRAWN on that date and leaves their classes, and the
 * homeroom teacher hears about it. Rejected stays rejected; withdrawn by the requester = cancelled. When the only
 * Owner or Admin asks, nobody else could decide, so it takes effect at once (and the log says so).
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    public static final String RECORD = "edu_withdrawal";
    public static final String WORKFLOW = "Student withdrawal";

    private final EducationAccess access;
    private final Enrollments enrollmentService;
    private final EduRefs refs;
    private final WithdrawalRepository withdrawals;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final AcademicYearRepository years;
    private final TeacherRepository teachers;
    private final MembershipRepository memberships;
    private final WorkflowService workflows;
    private final ApprovalService approvals;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    @Transactional
    public WithdrawalView request(String slug, UUID studentId, WithdrawalRequest in) {
        Edu edu = access.require(slug);
        Student s = students.lock(studentId, edu.orgId()).orElseThrow(BusinessException::notFound);
        if (!access.sees(edu, s.getId())) {
            throw BusinessException.notFound();
        }
        if (!edu.admin() && !homeroomTeacher(edu, s)) {
            throw BusinessException.forbidden();
        }
        if (!s.active()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This student is no longer at the school", List.of());
        }
        if (pending(s.getId())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "A withdrawal request for this student is waiting", List.of());
        }
        if (in.effectiveOn().isBefore(s.getAdmittedOn())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "effectiveOn", "This is before the admission date");
        }
        Withdrawal w = new Withdrawal();
        w.setId(Ids.next());
        w.setOrgId(edu.orgId());
        w.setStudentId(s.getId());
        w.setEffectiveOn(in.effectiveOn());
        w.setReason(in.reason().strip());
        w.setStatus(Withdrawal.PENDING);
        w.setRequestedBy(edu.userId());
        w.setCreatedAt(clock.instant());
        // Ids are assigned here, so save() merges: keep working on the managed copy it returns.
        w = withdrawals.saveAndFlush(w);
        activity.record(edu.orgId(), EducationAccess.MODULE, StudentService.RECORD, s.getId(), edu.userId(),
                "education.withdrawal.requested", Map.of("name", s.getNameEn(), "date", w.getEffectiveOn().toString(), "reason", w.getReason()));
        if (edu.admin() && noOtherAdmin(edu)) {
            approve(edu.ctx(), w);
            activity.record(edu.orgId(), EducationAccess.MODULE, StudentService.RECORD, s.getId(), edu.userId(),
                    "education.withdrawal.self_approved", Map.of("name", s.getNameEn()));
            return views(edu, List.of(w)).get(0);
        }
        UUID workflow = workflows.ensure(edu.ctx(), EducationAccess.MODULE, WORKFLOW, new StepInput("ORG_ROLE", null, "ADMIN"));
        ApprovalDetail d = approvals.submit(edu.slug(), new SubmitRequest(workflow, EducationAccess.MODULE, RECORD, w.getId(), w.getReason()));
        w.setApprovalId(d.summary().id());
        return views(edu, List.of(w)).get(0);
    }

    /** Requests waiting for a decision: all for admins, the caller's own for teachers. */
    @Transactional(readOnly = true)
    public List<WithdrawalView> waiting(Edu edu) {
        return views(edu, withdrawals.findByOrgIdAndStatusOrderByCreatedAtAsc(edu.orgId(), Withdrawal.PENDING).stream()
                .filter(w -> edu.admin() || w.getRequestedBy().equals(edu.userId())).toList());
    }

    @Transactional(readOnly = true)
    public List<WithdrawalView> forStudent(Edu edu, UUID studentId) {
        return views(edu, withdrawals.findByOrgIdAndStudentIdOrderByCreatedAtDesc(edu.orgId(), studentId));
    }

    public boolean pending(UUID studentId) {
        return withdrawals.existsByStudentIdAndStatus(studentId, Withdrawal.PENDING);
    }

    /** The approval engine closed a withdrawal's request: settle it. */
    @EventListener
    @Transactional
    public void onApprovalClosed(ApprovalClosed ev) {
        if (!EducationAccess.MODULE.equals(ev.module()) || !RECORD.equals(ev.recordType())) {
            return;
        }
        withdrawals.findByIdAndOrgId(ev.recordId(), ev.ctx().orgId())
                .filter(w -> Withdrawal.PENDING.equals(w.getStatus()) && ev.requestId().equals(w.getApprovalId()))
                .ifPresent(w -> {
                    switch (ev.status()) {
                        case "APPROVED" -> approve(ev.ctx(), w);
                        case "REJECTED" -> close(ev.ctx(), w, Withdrawal.REJECTED, "education.withdrawal.rejected");
                        default -> close(ev.ctx(), w, Withdrawal.CANCELLED, "education.withdrawal.cancelled");
                    }
                });
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    /** "Withdrawal · Sok Dara (STU-0001)". */
    static String title(StudentRepository students, Withdrawal w) {
        return students.findById(w.getStudentId()).map(s -> "Withdrawal · " + s.getNameEn() + " (" + s.getNumber() + ")").orElse("Withdrawal");
    }

    static String link(String slug, UUID studentId) {
        return "/" + slug + "/education/students/" + studentId;
    }

    /** The caller is the homeroom teacher of the student's class this year. */
    boolean homeroomTeacher(Edu edu, Student s) {
        if (edu.teacherId() == null) {
            return false;
        }
        AcademicYear y = years.findByOrgIdAndCurrentTrue(edu.orgId()).orElse(null);
        return enrollmentService.inYear(s.getId(), y == null ? null : y.getId())
                .flatMap(e -> classes.findById(e.getClassId())).map(c -> edu.teacherId().equals(c.getHomeroomTeacherId())).orElse(false);
    }

    private void approve(OrgContext ctx, Withdrawal w) {
        Student s = students.lock(w.getStudentId(), ctx.orgId()).orElseThrow();
        w.setStatus(Withdrawal.APPROVED);
        w.setDecidedAt(clock.instant());
        if (!s.active()) {
            return;
        }
        AcademicYear y = years.findByOrgIdAndCurrentTrue(ctx.orgId()).orElse(null);
        UUID homeroom = enrollmentService.inYear(s.getId(), y == null ? null : y.getId())
                .flatMap(e -> classes.findById(e.getClassId())).map(SchoolClass::getHomeroomTeacherId)
                .flatMap(teachers::findById).map(Teacher::getUserId).orElse(null);
        s.setStatus(Student.WITHDRAWN);
        s.setLeftOn(w.getEffectiveOn().isBefore(s.getAdmittedOn()) ? s.getAdmittedOn() : w.getEffectiveOn());
        s.setLeaveReason(w.getReason());
        s.setUpdatedAt(clock.instant());
        enrollmentService.leave(s.getId(), s.getLeftOn());
        activity.record(ctx.orgId(), EducationAccess.MODULE, StudentService.RECORD, s.getId(), ctx.userId(), "education.student.withdrawn",
                Map.of("name", s.getNameEn(), "date", s.getLeftOn().toString()));
        notifications.send(ctx.orgId(), java.util.stream.Stream.of(homeroom, w.getRequestedBy()).filter(Objects::nonNull).toList(),
                ctx.userId(), "education.student.withdrawn",
                Text.of("Student withdrawn: {name}", Map.of("name", s.getNameEn())), Text.raw(w.getReason()), link(ctx.slug(), s.getId()));
    }

    private void close(OrgContext ctx, Withdrawal w, String status, String verb) {
        w.setStatus(status);
        w.setDecidedAt(clock.instant());
        String name = students.findById(w.getStudentId()).map(Student::getNameEn).orElse("");
        activity.record(ctx.orgId(), EducationAccess.MODULE, StudentService.RECORD, w.getStudentId(), ctx.userId(), verb, Map.of("name", name));
    }

    private boolean noOtherAdmin(Edu edu) {
        return memberships.findByOrgIdOrderByCreatedAt(edu.orgId()).stream()
                .noneMatch(m -> Membership.ACTIVE.equals(m.getStatus()) && m.getRole().isAdmin() && !m.getUserId().equals(edu.userId()));
    }

    List<WithdrawalView> views(Edu edu, Collection<Withdrawal> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<UUID, Student> ss = new HashMap<>();
        students.findByIdInAndOrgId(found.stream().map(Withdrawal::getStudentId).distinct().toList(), edu.orgId()).forEach(s -> ss.put(s.getId(), s));
        Map<UUID, PersonRef> ps = refs.people(found.stream().map(Withdrawal::getRequestedBy).toList());
        Map<UUID, String> statuses = approvals.statuses(edu.orgId(), found.stream().map(Withdrawal::getApprovalId).filter(Objects::nonNull).toList());
        return found.stream().map(w -> new WithdrawalView(w.getId(), EduRefs.student(ss.get(w.getStudentId())), w.getEffectiveOn(),
                w.getReason(), w.getStatus(), w.getApprovalId(), w.getApprovalId() == null ? null : statuses.get(w.getApprovalId()),
                ps.get(w.getRequestedBy()), w.getCreatedAt(), w.getDecidedAt())).toList();
    }
}
