package com.businessplatform.education.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

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
import com.businessplatform.common.i18n.Text;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.education.domain.AcademicYear;
import com.businessplatform.education.domain.Enrollment;
import com.businessplatform.education.domain.FeeInvoice;
import com.businessplatform.education.domain.FeeItem;
import com.businessplatform.education.domain.FeePayment;
import com.businessplatform.education.domain.GradeLevel;
import com.businessplatform.education.domain.Grading;
import com.businessplatform.education.domain.SchoolClass;
import com.businessplatform.education.domain.Student;
import com.businessplatform.education.payload.EducationPayloads.ActivityView;
import com.businessplatform.education.payload.EducationPayloads.BillResult;
import com.businessplatform.education.payload.EducationPayloads.FeeItemRequest;
import com.businessplatform.education.payload.EducationPayloads.FeeItemView;
import com.businessplatform.education.payload.EducationPayloads.InvoiceRequest;
import com.businessplatform.education.payload.EducationPayloads.InvoiceSummary;
import com.businessplatform.education.payload.EducationPayloads.InvoiceView;
import com.businessplatform.education.payload.EducationPayloads.PaymentRequest;
import com.businessplatform.education.payload.EducationPayloads.PaymentView;
import com.businessplatform.education.payload.EducationPayloads.PersonRef;
import com.businessplatform.education.payload.EducationPayloads.VoidRequest;
import com.businessplatform.education.repository.EnrollmentRepository;
import com.businessplatform.education.repository.FeeInvoiceRepository;
import com.businessplatform.education.repository.FeeItemRepository;
import com.businessplatform.education.repository.FeePaymentRepository;
import com.businessplatform.education.repository.SchoolClassRepository;
import com.businessplatform.education.repository.StudentRepository;
import com.businessplatform.education.service.EducationAccess.Edu;
import com.businessplatform.notification.NotificationService;

import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * Fees, for admins only: fee items of an academic year (for one grade level or every grade), billed to the
 * students in that grade's classes as invoices (FEE-0001, one per student and item, at the item's amount), or one
 * invoice at a time; payments (at most the balance) move an invoice to PARTIALLY_PAID and PAID; an invoice with
 * nothing paid can be voided with a reason. Money is USD, computed here (half-up to cents).
 */
@Service
@RequiredArgsConstructor
public class FeeService {

    public static final String ITEM = "edu_fee_item";
    public static final String RECORD = "edu_invoice";
    static final int MAX_PAGE = 100;

    private final EducationAccess access;
    private final StructureService structure;
    private final EduRefs refs;
    private final FeeItemRepository items;
    private final FeeInvoiceRepository invoices;
    private final FeePaymentRepository payments;
    private final StudentRepository students;
    private final SchoolClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final ActivityService activity;
    private final NotificationService notifications;
    private final Clock clock;

    // ── Fee items ────────────────────────────────────────────────────────────────────

    /** The fee items of {@code yearId} (the current year when null), by due date. */
    @Transactional(readOnly = true)
    public List<FeeItemView> items(String slug, UUID yearId) {
        Edu edu = access.requireAdmin(slug);
        UUID year = yearId != null ? structure.year(edu, yearId).getId()
                : structure.currentYear(edu.orgId()).map(AcademicYear::getId).orElse(null);
        if (year == null) {
            return List.of();
        }
        return itemViews(edu, items.findByOrgIdAndYearIdOrderByDueOnAscNameEnAsc(edu.orgId(), year));
    }

    @Transactional
    public FeeItemView saveItem(String slug, UUID id, FeeItemRequest in) {
        Edu edu = access.requireAdmin(slug);
        FeeItem f;
        if (id == null) {
            f = new FeeItem();
            f.setId(Ids.next());
            f.setOrgId(edu.orgId());
            f.setCreatedAt(clock.instant());
        } else {
            f = item(edu, id);
        }
        AcademicYear y = structure.year(edu, in.yearId());
        if (id != null && !f.getYearId().equals(y.getId()) && !invoices.billedStudents(f.getId()).isEmpty()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "yearId", "This fee is already billed; it stays in its year");
        }
        GradeLevel g = in.gradeId() == null ? null : structure.grade(edu, in.gradeId());
        f.setYearId(y.getId());
        f.setGradeId(g == null ? null : g.getId());
        f.setNameEn(in.nameEn().strip());
        f.setNameKm(EduRefs.clean(in.nameKm()));
        f.setAmount(Grading.cents(in.amount()));
        f.setDueOn(in.dueOn());
        f.setActive(in.active() == null || in.active());
        items.saveAndFlush(f);
        activity.record(edu.orgId(), EducationAccess.MODULE, ITEM, f.getId(), edu.userId(),
                id == null ? "education.fee.created" : "education.fee.updated",
                Map.of("name", f.getNameEn(), "amount", f.getAmount().toPlainString()));
        return itemViews(edu, List.of(f)).get(0);
    }

    /**
     * Bills an active fee item to every active student in a class of its year (and grade) who hasn't been billed
     * for it yet. Due on the item's due date (today if that has passed).
     */
    @Transactional
    public BillResult bill(String slug, UUID id) {
        Edu edu = access.requireAdmin(slug);
        FeeItem f = item(edu, id);
        if (!f.isActive()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This fee is switched off", List.of());
        }
        List<UUID> classIds = classes.findByOrgIdAndYearIdOrderByName(edu.orgId(), f.getYearId()).stream()
                .filter(c -> f.getGradeId() == null || f.getGradeId().equals(c.getGradeId())).map(SchoolClass::getId).toList();
        Set<UUID> candidates = new HashSet<>();
        if (!classIds.isEmpty()) {
            enrollments.findByClassIdIn(classIds).stream().filter(e -> e.getLeftOn() == null).map(Enrollment::getStudentId)
                    .forEach(candidates::add);
        }
        Set<UUID> already = new HashSet<>(invoices.billedStudents(f.getId()));
        LocalDate today = access.today();
        int created = 0;
        int skipped = 0;
        for (Student s : students.findByIdInAndOrgId(candidates, edu.orgId()).stream()
                .sorted(java.util.Comparator.comparing(Student::getNumber)).toList()) {
            if (!s.active() || already.contains(s.getId())) {
                skipped++;
                continue;
            }
            invoice(edu, s, f, f.getNameEn(), f.getAmount(), f.getDueOn().isBefore(today) ? today : f.getDueOn());
            created++;
        }
        activity.record(edu.orgId(), EducationAccess.MODULE, ITEM, f.getId(), edu.userId(), "education.fee.billed",
                Map.of("name", f.getNameEn(), "count", created));
        return new BillResult(created, skipped);
    }

    // ── Invoices ─────────────────────────────────────────────────────────────────────

    /** {@code q}: number or student name / number; {@code status}: a stored status or OVERDUE. Newest first. */
    @Transactional(readOnly = true)
    public PageResponse<InvoiceSummary> list(String slug, String q, String status, UUID studentId, UUID feeItemId, int page, int size) {
        Edu edu = access.requireAdmin(slug);
        LocalDate today = access.today();
        Specification<FeeInvoice> spec = (x, query, cb) -> cb.equal(x.get("orgId"), edu.orgId());
        if (studentId != null) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("studentId"), studentId));
        }
        if (feeItemId != null) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("feeItemId"), feeItemId));
        }
        if ("OVERDUE".equals(status)) {
            spec = spec.and((x, query, cb) -> cb.and(x.get("status").in(FeeInvoice.ISSUED, FeeInvoice.PARTIALLY_PAID),
                    cb.lessThan(x.get("dueOn"), today)));
        } else if ("OPEN".equals(status)) {
            spec = spec.and((x, query, cb) -> x.get("status").in(FeeInvoice.ISSUED, FeeInvoice.PARTIALLY_PAID));
        } else if (status != null && !status.isBlank()) {
            spec = spec.and((x, query, cb) -> cb.equal(x.get("status"), status));
        }
        if (q != null && !q.isBlank()) {
            String like = EduRefs.like(q);
            spec = spec.and((x, query, cb) -> {
                Subquery<UUID> named = query.subquery(UUID.class);
                Root<Student> s = named.from(Student.class);
                named.select(s.get("id")).where(cb.equal(s.get("orgId"), edu.orgId()), cb.or(cb.like(cb.lower(s.get("nameEn")), like, '\\'),
                        cb.like(cb.lower(s.get("number")), like, '\\')));
                return cb.or(cb.like(cb.lower(x.get("number")), like, '\\'), x.get("studentId").in(named));
            });
        }
        Page<FeeInvoice> found = invoices.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE),
                Sort.by(Sort.Direction.DESC, "createdAt", "number")));
        return PageResponse.of(new PageImpl<>(summaries(edu, found.getContent()), found.getPageable(), found.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public InvoiceView get(String slug, UUID id) {
        Edu edu = access.requireAdmin(slug);
        return view(edu, find(edu, id));
    }

    /** One invoice for one student: a fee item's (its name, amount and due date fill empty fields) or free text. */
    @Transactional
    public InvoiceView create(String slug, InvoiceRequest in) {
        Edu edu = access.requireAdmin(slug);
        Student s = students.lock(in.studentId(), edu.orgId())
                .orElseThrow(() -> BusinessException.field(ErrorCode.VALIDATION_FAILED, "studentId", "Choose a student"));
        if (!s.active()) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "studentId", "This student is no longer at the school");
        }
        FeeItem f = in.feeItemId() == null ? null : item(edu, in.feeItemId());
        if (f != null && invoices.billedStudents(f.getId()).contains(s.getId())) {
            throw BusinessException.field(ErrorCode.CONFLICT, "feeItemId", "This student is already billed for this fee");
        }
        String description = EduRefs.clean(in.description()) != null ? in.description().strip() : f == null ? null : f.getNameEn();
        BigDecimal amount = in.amount() != null ? Grading.cents(in.amount()) : f == null ? null : f.getAmount();
        if (description == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "description", "Describe the fee or choose a fee item");
        }
        if (amount == null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "amount", "Required");
        }
        LocalDate today = access.today();
        LocalDate due = in.dueOn() != null ? in.dueOn() : f != null && !f.getDueOn().isBefore(today) ? f.getDueOn() : today;
        if (due.isBefore(today)) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "dueOn", "The due date is in the past");
        }
        return view(edu, invoice(edu, s, f, description, amount, due));
    }

    /** Records money received: at most the balance. */
    @Transactional
    public InvoiceView pay(String slug, UUID id, PaymentRequest in) {
        Edu edu = access.requireAdmin(slug);
        FeeInvoice x = lock(edu, id);
        if (!x.open()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Payments go on an invoice that isn't paid yet", List.of());
        }
        BigDecimal amount = Grading.cents(in.amount());
        if (amount.compareTo(x.balance()) > 0) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "amount", "This is more than the balance");
        }
        if (in.paidOn().isAfter(access.today())) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "paidOn", "This date is in the future");
        }
        FeePayment p = new FeePayment();
        p.setId(Ids.next());
        p.setOrgId(edu.orgId());
        p.setInvoiceId(x.getId());
        p.setPaidOn(in.paidOn());
        p.setAmount(amount);
        p.setMethod(in.method());
        p.setReference(EduRefs.clean(in.reference()));
        p.setRecordedBy(edu.userId());
        p.setCreatedAt(clock.instant());
        payments.save(p);
        x.setPaid(x.getPaid().add(amount));
        settle(x);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, x.getId(), edu.userId(), "education.invoice.payment",
                Map.of("number", x.getNumber(), "amount", amount.toPlainString(), "method", p.getMethod()));
        if (FeeInvoice.PAID.equals(x.getStatus())) {
            activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, x.getId(), edu.userId(), "education.invoice.paid",
                    Map.of("number", x.getNumber()));
            notifications.send(edu.orgId(), List.of(x.getCreatedBy()), edu.userId(), "education.invoice.paid",
                    Text.of("Fees paid: {number}", Map.of("number", x.getNumber())),
                    Text.raw(students.findById(x.getStudentId()).map(Student::getNameEn).orElse("")), link(edu, x.getId()));
        }
        return view(edu, x);
    }

    @Transactional
    public InvoiceView removePayment(String slug, UUID id, UUID paymentId) {
        Edu edu = access.requireAdmin(slug);
        FeeInvoice x = lock(edu, id);
        FeePayment p = payments.findByIdAndInvoiceId(paymentId, x.getId()).orElseThrow(BusinessException::notFound);
        if (FeeInvoice.VOID.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is void", List.of());
        }
        payments.delete(p);
        x.setPaid(x.getPaid().subtract(p.getAmount()));
        settle(x);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, x.getId(), edu.userId(), "education.invoice.payment_removed",
                Map.of("number", x.getNumber(), "amount", p.getAmount().toPlainString()));
        return view(edu, x);
    }

    @Transactional
    public InvoiceView voidInvoice(String slug, UUID id, VoidRequest in) {
        Edu edu = access.requireAdmin(slug);
        FeeInvoice x = lock(edu, id);
        if (FeeInvoice.VOID.equals(x.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This invoice is void", List.of());
        }
        if (x.getPaid().signum() > 0) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Remove its payments before voiding this invoice", List.of());
        }
        x.setStatus(FeeInvoice.VOID);
        x.setVoidReason(in.reason().strip());
        x.setVoidedAt(clock.instant());
        x.setUpdatedAt(clock.instant());
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, x.getId(), edu.userId(), "education.invoice.voided",
                Map.of("number", x.getNumber(), "reason", x.getVoidReason()));
        return view(edu, x);
    }

    @Transactional(readOnly = true)
    public List<ActivityView> history(String slug, UUID id) {
        Edu edu = access.requireAdmin(slug);
        FeeInvoice x = find(edu, id);
        List<Activity> as = activity.forRecord(edu.orgId(), RECORD, x.getId());
        Map<UUID, PersonRef> ps = refs.people(as.stream().map(Activity::getActorId).toList());
        return as.stream().map(a -> new ActivityView(a.getId(), ps.get(a.getActorId()), a.getVerb(), a.getData(), a.getCreatedAt())).toList();
    }

    // ── For the rest of the module ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<InvoiceSummary> forStudent(Edu edu, UUID studentId) {
        return summaries(edu, invoices.findByOrgIdAndStudentIdOrderByIssuedOnDescNumberDesc(edu.orgId(), studentId));
    }

    @Transactional(readOnly = true)
    public BigDecimal balance(Edu edu, UUID studentId) {
        return invoices.balances(edu.orgId(), List.of(studentId)).stream().findFirst().map(r -> EduRefs.money(r[1]))
                .orElse(BigDecimal.ZERO.setScale(2));
    }

    static String link(Edu edu, UUID id) {
        return "/" + edu.slug() + "/education/fees/invoices/" + id;
    }

    FeeItem item(Edu edu, UUID id) {
        return items.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    private FeeInvoice find(Edu edu, UUID id) {
        return invoices.findByIdAndOrgId(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    private FeeInvoice lock(Edu edu, UUID id) {
        return invoices.lock(id, edu.orgId()).orElseThrow(BusinessException::notFound);
    }

    private FeeInvoice invoice(Edu edu, Student s, FeeItem f, String description, BigDecimal amount, LocalDate due) {
        FeeInvoice x = new FeeInvoice();
        x.setId(Ids.next());
        x.setOrgId(edu.orgId());
        x.setNumber(structure.nextInvoiceNumber(edu));
        x.setStudentId(s.getId());
        x.setFeeItemId(f == null ? null : f.getId());
        x.setDescription(description);
        x.setAmount(amount);
        x.setPaid(BigDecimal.ZERO.setScale(2));
        x.setIssuedOn(access.today());
        x.setDueOn(due);
        x.setStatus(FeeInvoice.ISSUED);
        x.setCreatedBy(edu.userId());
        x.setCreatedAt(clock.instant());
        x.setUpdatedAt(x.getCreatedAt());
        invoices.saveAndFlush(x);
        activity.record(edu.orgId(), EducationAccess.MODULE, RECORD, x.getId(), edu.userId(), "education.invoice.created",
                Map.of("number", x.getNumber(), "student", s.getNameEn(), "amount", amount.toPlainString()));
        activity.record(edu.orgId(), EducationAccess.MODULE, StudentService.RECORD, s.getId(), edu.userId(), "education.student.billed",
                Map.of("number", x.getNumber(), "description", description, "amount", amount.toPlainString()));
        return x;
    }

    private void settle(FeeInvoice x) {
        x.setStatus(x.getPaid().compareTo(x.getAmount()) >= 0 ? FeeInvoice.PAID
                : x.getPaid().signum() > 0 ? FeeInvoice.PARTIALLY_PAID : FeeInvoice.ISSUED);
        x.setUpdatedAt(clock.instant());
    }

    List<InvoiceSummary> summaries(Edu edu, Collection<FeeInvoice> found) {
        Map<UUID, Student> ss = new HashMap<>();
        students.findByIdInAndOrgId(found.stream().map(FeeInvoice::getStudentId).distinct().toList(), edu.orgId())
                .forEach(s -> ss.put(s.getId(), s));
        LocalDate today = access.today();
        return found.stream().map(x -> new InvoiceSummary(x.getId(), x.getNumber(), EduRefs.student(ss.get(x.getStudentId())),
                x.getDescription(), x.getAmount(), x.getPaid(), x.balance(), x.getIssuedOn(), x.getDueOn(), x.getStatus(),
                x.overdue(today))).toList();
    }

    private InvoiceView view(Edu edu, FeeInvoice x) {
        List<FeePayment> ps = payments.findByInvoiceIdOrderByPaidOnAscCreatedAtAsc(x.getId());
        Map<UUID, PersonRef> who = refs.people(Stream.concat(Stream.of(x.getCreatedBy()), ps.stream().map(FeePayment::getRecordedBy)).toList());
        return new InvoiceView(summaries(edu, List.of(x)).get(0), x.getFeeItemId(),
                ps.stream().map(p -> new PaymentView(p.getId(), p.getPaidOn(), p.getAmount(), p.getMethod(), p.getReference(),
                        who.get(p.getRecordedBy()), p.getCreatedAt())).toList(),
                x.getVoidReason(), x.getVoidedAt(), who.get(x.getCreatedBy()), x.getCreatedAt(), x.open(),
                x.open() && x.getPaid().signum() == 0, !FeeInvoice.VOID.equals(x.getStatus()) && !ps.isEmpty());
    }

    private List<FeeItemView> itemViews(Edu edu, List<FeeItem> found) {
        var years = refs.years(edu.orgId());
        var grades = refs.grades(edu.orgId());
        List<FeeInvoice> billed = found.isEmpty() ? List.of() : invoices.findByFeeItemIdIn(found.stream().map(FeeItem::getId).toList());
        return found.stream().map(f -> {
            List<FeeInvoice> mine = billed.stream().filter(i -> f.getId().equals(i.getFeeItemId()) && !FeeInvoice.VOID.equals(i.getStatus())).toList();
            BigDecimal collected = mine.stream().map(FeeInvoice::getPaid).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            BigDecimal outstanding = mine.stream().map(FeeInvoice::balance).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            return new FeeItemView(f.getId(), f.getYearId(), years.containsKey(f.getYearId()) ? years.get(f.getYearId()).getName() : "",
                    f.getGradeId(), f.getGradeId() == null ? null : EduRefs.gradeName(grades.get(f.getGradeId())), f.getNameEn(),
                    f.getNameKm(), f.getAmount(), f.getDueOn(), f.isActive(), mine.size(), collected, outstanding);
        }).toList();
    }
}
