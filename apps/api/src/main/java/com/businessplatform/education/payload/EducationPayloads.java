package com.businessplatform.education.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Education: structure, people, classes, timetable, attendance, assessments, results, fees and withdrawals. */
public final class EducationPayloads {

    private EducationPayloads() {
    }

    private static final String PHONE = "^$|^[0-9+()\\-. ]{6,30}$";
    private static final String PHONE_MESSAGE = "Use digits, spaces and + ( ) - only";
    private static final String CENTS = "Use at most 2 decimals";
    private static final String RELATIONSHIP = "MOTHER|FATHER|GUARDIAN|GRANDPARENT|SIBLING|OTHER";

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** Empty or null clears a text field. */
    public record SettingsRequest(@Size(max = 200) String schoolName, @Size(max = 120) String principalName,
            @Size(max = 500) String address, @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2, message = CENTS) BigDecimal passMark) {
    }

    public record YearRequest(@NotBlank @Size(max = 40) String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn,
            boolean current) {
    }

    public record TermRequest(@NotBlank @Size(max = 40) String name, @NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }

    public record GradeRequest(@NotBlank @Size(max = 60) String nameEn, @Size(max = 60) String nameKm,
            @NotNull @Min(0) @Max(99) Integer position, Boolean active) {
    }

    public record SubjectRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{1,12}$", message = "1–12 letters, digits or -") String code,
            @NotBlank @Size(max = 80) String nameEn, @Size(max = 80) String nameKm, Boolean active) {
    }

    /** {@code userId}: the member to make a teacher (when adding; ignored when editing). */
    public record TeacherRequest(UUID userId, @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @Size(max = 120) String specialty, Boolean active) {
    }

    /** A phone number or an email is needed to reach them. */
    public record GuardianRequest(@NotBlank @Size(max = 120) String name, @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @Email @Size(max = 160) String email, @Size(max = 120) String occupation, @Size(max = 500) String address) {
    }

    /** An existing guardian ({@code guardianId}) or a new one ({@code guardian}), and how they are related. */
    public record GuardianLink(UUID guardianId, @Valid GuardianRequest guardian,
            @NotNull @Pattern(regexp = RELATIONSHIP) String relationship, boolean primary) {
    }

    public record StudentRequest(@NotBlank @Size(max = 120) String nameEn, @Size(max = 120) String nameKm,
            @NotNull @Pattern(regexp = "FEMALE|MALE|OTHER") String gender, @NotNull LocalDate dateOfBirth,
            @NotNull LocalDate admittedOn, @Size(max = 500) String address, @Size(max = 1000) String medicalNotes) {
    }

    /** Registration: the student, at least one guardian, and optionally a class of the current year. */
    public record RegisterRequest(@NotBlank @Size(max = 120) String nameEn, @Size(max = 120) String nameKm,
            @NotNull @Pattern(regexp = "FEMALE|MALE|OTHER") String gender, @NotNull LocalDate dateOfBirth,
            @NotNull LocalDate admittedOn, @Size(max = 500) String address, @Size(max = 1000) String medicalNotes,
            UUID classId, @NotEmpty @Size(max = 4) List<@Valid @NotNull GuardianLink> guardians) {

        public StudentRequest student() {
            return new StudentRequest(nameEn, nameKm, gender, dateOfBirth, admittedOn, address, medicalNotes);
        }
    }

    public record GraduateRequest(@NotNull LocalDate leftOn) {
    }

    public record WithdrawalRequest(@NotNull LocalDate effectiveOn, @NotBlank @Size(max = 500) String reason) {
    }

    public record ClassRequest(@NotNull UUID yearId, @NotNull UUID gradeId, @NotBlank @Size(max = 40) String name,
            UUID homeroomTeacherId, @Size(max = 40) String room, @Min(1) @Max(200) Integer capacity) {
    }

    public record EnrollRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> studentIds) {
    }

    public record CourseRequest(@NotNull UUID subjectId, @NotNull UUID teacherId) {
    }

    public record CourseTeacherRequest(@NotNull UUID teacherId) {
    }

    /** {@code weekday} 1 = Monday … 6 = Saturday. */
    public record PeriodRequest(@NotNull UUID courseId, @NotNull @Min(1) @Max(6) Integer weekday,
            @NotNull LocalTime startsAt, @NotNull LocalTime endsAt, @Size(max = 40) String room) {
    }

    public record AttendanceEntry(@NotNull UUID studentId, @NotNull @Pattern(regexp = "PRESENT|ABSENT|LATE|EXCUSED") String status,
            @Size(max = 200) String note) {
    }

    public record AttendanceRequest(@NotNull LocalDate day, @NotEmpty @Size(max = 200) List<@Valid @NotNull AttendanceEntry> entries) {
    }

    public record AssessmentRequest(@NotNull UUID termId, @NotBlank @Size(max = 80) String name,
            @NotNull @Pattern(regexp = "EXAM|QUIZ|ASSIGNMENT|PROJECT|ORAL") String kind, @NotNull LocalDate heldOn,
            @NotNull @DecimalMin("0.01") @DecimalMax("1000") @Digits(integer = 4, fraction = 2, message = CENTS) BigDecimal maxScore,
            @NotNull @DecimalMin("0.01") @DecimalMax("100") @Digits(integer = 3, fraction = 2, message = CENTS) BigDecimal weight) {
    }

    /** {@code score} null clears it (not graded). */
    public record ScoreEntry(@NotNull UUID studentId,
            @DecimalMin("0") @Digits(integer = 4, fraction = 2, message = CENTS) BigDecimal score) {
    }

    public record ScoresRequest(@NotEmpty @Size(max = 200) List<@Valid @NotNull ScoreEntry> scores) {
    }

    /** {@code gradeId} null = every grade. */
    public record FeeItemRequest(@NotNull UUID yearId, UUID gradeId, @NotBlank @Size(max = 120) String nameEn,
            @Size(max = 120) String nameKm,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal amount,
            @NotNull LocalDate dueOn, Boolean active) {
    }

    /** One invoice for one student: a fee item (its name and amount fill empty fields) or free text. */
    public record InvoiceRequest(@NotNull UUID studentId, UUID feeItemId, @Size(max = 200) String description,
            @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal amount,
            LocalDate dueOn) {
    }

    public record PaymentRequest(@NotNull LocalDate paidOn,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal amount,
            @NotNull @Pattern(regexp = "CASH|BANK_TRANSFER|KHQR|CARD|OTHER") String method, @Size(max = 120) String reference) {
    }

    public record VoidRequest(@NotBlank @Size(max = 500) String reason) {
    }

    // ── Views ────────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    public record SettingsView(String schoolName, String principalName, String address, String phone, BigDecimal passMark,
            String orgName) {
    }

    public record TermView(UUID id, UUID yearId, String name, LocalDate startsOn, LocalDate endsOn) {
    }

    public record YearView(UUID id, String name, LocalDate startsOn, LocalDate endsOn, boolean current, List<TermView> terms) {
    }

    public record GradeView(UUID id, String nameEn, String nameKm, int position, boolean active) {
    }

    public record SubjectView(UUID id, String code, String nameEn, String nameKm, boolean active) {
    }

    public record TeacherRef(UUID id, UUID userId, String name, String avatarUrl) {
    }

    /** {@code homerooms}: the names of the current year's classes they lead; {@code courses}: how many they teach then. */
    public record TeacherView(UUID id, UUID userId, String name, String email, String avatarUrl, String phone, String specialty,
            boolean active, List<String> homerooms, int courses) {
    }

    public record ClassRef(UUID id, String name, UUID yearId) {
    }

    public record StudentRef(UUID id, String number, String nameEn, String nameKm, String status) {
    }

    public record GuardianView(UUID id, String name, String phone, String email, String occupation, String address,
            List<StudentRef> children) {
    }

    /** A guardian of one student. */
    public record GuardianOf(UUID id, String name, String phone, String email, String occupation, String address,
            String relationship, boolean primary) {
    }

    /** {@code primaryGuardian}: the main contact's name and phone; {@code balance}: unpaid fees (null unless admin). */
    public record StudentSummary(UUID id, String number, String nameEn, String nameKm, String gender, LocalDate dateOfBirth,
            String status, ClassRef currentClass, String gradeName, String primaryGuardian, String guardianPhone,
            BigDecimal balance) {
    }

    public record StudentView(UUID id, String number, String nameEn, String nameKm, String gender, LocalDate dateOfBirth,
            LocalDate admittedOn, String address, String medicalNotes, String status, LocalDate leftOn, String leaveReason,
            Instant createdAt) {
    }

    public record EnrollmentView(ClassRef classRef, String yearName, String gradeName, LocalDate enrolledOn, LocalDate leftOn) {
    }

    public record AttendanceCounts(long present, long late, long absent, long excused, BigDecimal rate) {
    }

    public record WithdrawalView(UUID id, StudentRef student, LocalDate effectiveOn, String reason, String status,
            UUID approvalId, String approvalStatus, PersonRef requestedBy, Instant createdAt, Instant decidedAt) {
    }

    /**
     * A student's page. {@code invoices} and {@code balance} only for admins (null otherwise); {@code attendance}
     * for the current academic year.
     */
    public record StudentDetail(StudentView student, List<GuardianOf> guardians, ClassRef currentClass, String gradeName,
            List<EnrollmentView> enrollments, AttendanceCounts attendance, BigDecimal balance, List<InvoiceSummary> invoices,
            List<WithdrawalView> withdrawals, boolean canEdit, boolean canRequestWithdrawal, boolean canGraduate) {
    }

    public record ClassSummary(UUID id, String name, UUID yearId, String yearName, UUID gradeId, String gradeName,
            TeacherRef homeroom, String room, Integer capacity, long students, int courses, boolean mine) {
    }

    public record CourseView(UUID id, ClassRef classRef, SubjectView subject, TeacherRef teacher, int periods, boolean mine) {
    }

    public record ClassDetail(ClassSummary summary, List<StudentSummary> students, List<CourseView> courses,
            boolean canManage, boolean canTakeAttendance) {
    }

    public record PeriodView(UUID id, ClassRef classRef, UUID courseId, SubjectView subject, TeacherRef teacher, int weekday,
            LocalTime startsAt, LocalTime endsAt, String room, boolean canEdit) {
    }

    public record AttendanceRow(StudentRef student, String status, String note) {
    }

    /** One class on one day; {@code marked}: attendance was taken. */
    public record AttendanceSheet(ClassRef classRef, LocalDate day, boolean marked, List<AttendanceRow> rows,
            AttendanceCounts counts, boolean canEdit, PersonRef recordedBy, Instant recordedAt) {
    }

    public record AttendanceSummaryRow(StudentRef student, AttendanceCounts counts) {
    }

    public record AttendanceSummary(ClassRef classRef, LocalDate from, LocalDate to, long days, List<AttendanceSummaryRow> rows,
            AttendanceCounts total) {
    }

    public record CourseRef(UUID id, ClassRef classRef, SubjectView subject, TeacherRef teacher) {
    }

    public record AssessmentView(UUID id, CourseRef course, TermView term, String name, String kind, LocalDate heldOn,
            BigDecimal maxScore, BigDecimal weight, long graded, long students, boolean canEdit) {
    }

    /** {@code scores}: assessment id → score (absent = not graded). */
    public record GradebookRow(StudentRef student, Map<UUID, BigDecimal> scores, BigDecimal result, String letter) {
    }

    public record Gradebook(CourseRef course, TermView term, List<AssessmentView> assessments, List<GradebookRow> rows,
            BigDecimal totalWeight, BigDecimal classAverage, boolean canEdit) {
    }

    /** {@code results}: course id → result in percent (absent = nothing graded). */
    public record ResultRow(StudentRef student, Map<UUID, BigDecimal> results, BigDecimal average, String letter, Integer rank,
            Boolean passed) {
    }

    public record ClassResults(ClassRef classRef, TermView term, List<CourseRef> courses, List<ResultRow> rows,
            BigDecimal passMark) {
    }

    public record ReportCardLine(SubjectView subject, String teacher, BigDecimal result, String letter) {
    }

    public record ReportCard(SettingsView school, StudentView student, ClassRef classRef, String gradeName, String homeroom,
            String yearName, TermView term, List<ReportCardLine> lines, BigDecimal average, String letter, Integer rank,
            int classSize, Boolean passed, AttendanceCounts attendance) {
    }

    public record FeeItemView(UUID id, UUID yearId, String yearName, UUID gradeId, String gradeName, String nameEn,
            String nameKm, BigDecimal amount, LocalDate dueOn, boolean active, long billed, BigDecimal collected,
            BigDecimal outstanding) {
    }

    public record InvoiceSummary(UUID id, String number, StudentRef student, String description, BigDecimal amount,
            BigDecimal paid, BigDecimal balance, LocalDate issuedOn, LocalDate dueOn, String status, boolean overdue) {
    }

    public record PaymentView(UUID id, LocalDate paidOn, BigDecimal amount, String method, String reference,
            PersonRef recordedBy, Instant createdAt) {
    }

    public record InvoiceView(InvoiceSummary summary, UUID feeItemId, List<PaymentView> payments, String voidReason,
            Instant voidedAt, PersonRef createdBy, Instant createdAt, boolean canPay, boolean canVoid, boolean canRemovePayment) {
    }

    public record BillResult(int created, int skipped) {
    }

    public record ActivityView(UUID id, PersonRef actor, String verb, Map<String, Object> data, Instant at) {
    }

    // ── Overview and reports ─────────────────────────────────────────────────────────

    /** One week (Monday) of attendance marks in the caller's classes. */
    public record WeekRate(LocalDate week, long present, long late, long absent, long excused, BigDecimal rate) {
    }

    /**
     * The education home. Admins see the whole school (fees too); teachers their own classes ({@code outstanding},
     * {@code overdue} and {@code overdueInvoices} null).
     */
    public record Overview(boolean admin, YearView currentYear, long activeStudents, long teachers, long classes,
            BigDecimal attendanceToday, int classesMarkedToday, List<ClassSummary> unmarkedToday, List<WeekRate> weeks,
            BigDecimal outstanding, BigDecimal overdue, Long overdueInvoices, List<AssessmentView> upcoming,
            List<WithdrawalView> pendingWithdrawals) {
    }

    public record AttendanceByClass(ClassRef classRef, String gradeName, long days, AttendanceCounts counts) {
    }

    public record ResultsByClass(ClassRef classRef, String gradeName, long students, long graded, BigDecimal average,
            BigDecimal passRate) {
    }

    public record FeesByGrade(UUID gradeId, String gradeName, long invoices, BigDecimal billed, BigDecimal collected,
            BigDecimal outstanding, BigDecimal overdue) {
    }

    /** {@code fees} null unless admin; {@code results} null when no term is chosen. */
    public record Reports(LocalDate from, LocalDate to, TermView term, List<AttendanceByClass> attendance,
            List<ResultsByClass> results, List<FeesByGrade> fees) {
    }
}
