package com.businessplatform.education.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.education.payload.EducationPayloads.*;
import com.businessplatform.education.service.AttendanceService;
import com.businessplatform.education.service.ClassService;
import com.businessplatform.education.service.FeeService;
import com.businessplatform.education.service.GradebookService;
import com.businessplatform.education.service.TimetableService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Education: classes and courses, the timetable, attendance, assessments, the gradebook, results and fees. */
@RestController
@RequestMapping("/api/orgs/{org}/education")
@RequiredArgsConstructor
@Tag(name = "Education")
public class EduTeachingController {

    private final ClassService classes;
    private final TimetableService timetable;
    private final AttendanceService attendance;
    private final GradebookService gradebook;
    private final FeeService fees;

    // ── Classes and courses ──────────────────────────────────────────────────────────

    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<ClassSummary>>> classes(@PathVariable String org, @RequestParam(required = false) UUID yearId) {
        return ResponseEntity.ok(ApiResponse.ok(classes.list(org, yearId)));
    }

    @PostMapping("/classes")
    public ResponseEntity<ApiResponse<ClassDetail>> createClass(@PathVariable String org, @Valid @RequestBody ClassRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(classes.save(org, null, in)));
    }

    @GetMapping("/classes/{id}")
    public ResponseEntity<ApiResponse<ClassDetail>> getClass(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(classes.get(org, id)));
    }

    @PutMapping("/classes/{id}")
    public ResponseEntity<ApiResponse<ClassDetail>> updateClass(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody ClassRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(classes.save(org, id, in)));
    }

    @PostMapping("/classes/{id}/enrollments")
    public ResponseEntity<ApiResponse<ClassDetail>> enroll(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody EnrollRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(classes.enroll(org, id, in)));
    }

    @DeleteMapping("/classes/{id}/enrollments/{studentId}")
    public ResponseEntity<ApiResponse<ClassDetail>> unenroll(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.ok(classes.unenroll(org, id, studentId)));
    }

    @GetMapping("/courses")
    public ResponseEntity<ApiResponse<List<CourseView>>> courses(@PathVariable String org, @RequestParam(required = false) UUID classId) {
        return ResponseEntity.ok(ApiResponse.ok(classes.courses(org, classId)));
    }

    @PostMapping("/classes/{id}/courses")
    public ResponseEntity<ApiResponse<CourseView>> addCourse(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody CourseRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(classes.addCourse(org, id, in)));
    }

    @PutMapping("/courses/{id}")
    public ResponseEntity<ApiResponse<CourseView>> changeTeacher(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CourseTeacherRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(classes.changeTeacher(org, id, in.teacherId())));
    }

    @DeleteMapping("/courses/{id}")
    public ResponseEntity<ApiResponse<Void>> removeCourse(@PathVariable String org, @PathVariable UUID id) {
        classes.removeCourse(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Timetable ────────────────────────────────────────────────────────────────────

    @GetMapping("/timetable")
    public ResponseEntity<ApiResponse<List<PeriodView>>> timetable(@PathVariable String org, @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(timetable.list(org, classId, teacherId)));
    }

    @PostMapping("/timetable")
    public ResponseEntity<ApiResponse<PeriodView>> addPeriod(@PathVariable String org, @Valid @RequestBody PeriodRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(timetable.save(org, null, in)));
    }

    @PutMapping("/timetable/{id}")
    public ResponseEntity<ApiResponse<PeriodView>> updatePeriod(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody PeriodRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(timetable.save(org, id, in)));
    }

    @DeleteMapping("/timetable/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePeriod(@PathVariable String org, @PathVariable UUID id) {
        timetable.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Attendance ───────────────────────────────────────────────────────────────────

    @GetMapping("/classes/{id}/attendance")
    public ResponseEntity<ApiResponse<AttendanceSheet>> sheet(@PathVariable String org, @PathVariable UUID id, @RequestParam LocalDate day) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.sheet(org, id, day)));
    }

    @PutMapping("/classes/{id}/attendance")
    public ResponseEntity<ApiResponse<AttendanceSheet>> takeAttendance(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AttendanceRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.save(org, id, in)));
    }

    @GetMapping("/classes/{id}/attendance/summary")
    public ResponseEntity<ApiResponse<AttendanceSummary>> attendanceSummary(@PathVariable String org, @PathVariable UUID id,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(attendance.summary(org, id, from, to)));
    }

    // ── Assessments, gradebook and results ───────────────────────────────────────────

    @GetMapping("/assessments")
    public ResponseEntity<ApiResponse<List<AssessmentView>>> assessments(@PathVariable String org,
            @RequestParam(required = false) UUID courseId, @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.list(org, courseId, termId)));
    }

    @PostMapping("/courses/{id}/assessments")
    public ResponseEntity<ApiResponse<AssessmentView>> createAssessment(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AssessmentRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(gradebook.save(org, id, null, in)));
    }

    @PutMapping("/assessments/{id}")
    public ResponseEntity<ApiResponse<AssessmentView>> updateAssessment(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AssessmentRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.save(org, null, id, in)));
    }

    @DeleteMapping("/assessments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAssessment(@PathVariable String org, @PathVariable UUID id) {
        gradebook.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/courses/{id}/gradebook")
    public ResponseEntity<ApiResponse<Gradebook>> gradebook(@PathVariable String org, @PathVariable UUID id, @RequestParam UUID termId) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.gradebook(org, id, termId)));
    }

    @PutMapping("/assessments/{id}/scores")
    public ResponseEntity<ApiResponse<Gradebook>> scores(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody ScoresRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.saveScores(org, id, in)));
    }

    @GetMapping("/classes/{id}/results")
    public ResponseEntity<ApiResponse<ClassResults>> results(@PathVariable String org, @PathVariable UUID id, @RequestParam UUID termId) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.classResults(org, id, termId)));
    }

    @GetMapping("/students/{id}/report-card")
    public ResponseEntity<ApiResponse<ReportCard>> reportCard(@PathVariable String org, @PathVariable UUID id, @RequestParam UUID termId) {
        return ResponseEntity.ok(ApiResponse.ok(gradebook.reportCard(org, id, termId)));
    }

    // ── Fees ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/fee-items")
    public ResponseEntity<ApiResponse<List<FeeItemView>>> feeItems(@PathVariable String org, @RequestParam(required = false) UUID yearId) {
        return ResponseEntity.ok(ApiResponse.ok(fees.items(org, yearId)));
    }

    @PostMapping("/fee-items")
    public ResponseEntity<ApiResponse<FeeItemView>> createFeeItem(@PathVariable String org, @Valid @RequestBody FeeItemRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(fees.saveItem(org, null, in)));
    }

    @PutMapping("/fee-items/{id}")
    public ResponseEntity<ApiResponse<FeeItemView>> updateFeeItem(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody FeeItemRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(fees.saveItem(org, id, in)));
    }

    @PostMapping("/fee-items/{id}/bill")
    public ResponseEntity<ApiResponse<BillResult>> bill(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(fees.bill(org, id)));
    }

    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceSummary>>> invoices(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID studentId, @RequestParam(required = false) UUID feeItemId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(fees.list(org, q, status, studentId, feeItemId, page, size)));
    }

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<InvoiceView>> createInvoice(@PathVariable String org, @Valid @RequestBody InvoiceRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(fees.create(org, in)));
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> invoice(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(fees.get(org, id)));
    }

    @GetMapping("/invoices/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityView>>> invoiceHistory(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(fees.history(org, id)));
    }

    @PostMapping("/invoices/{id}/payments")
    public ResponseEntity<ApiResponse<InvoiceView>> pay(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody PaymentRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(fees.pay(org, id, in)));
    }

    @DeleteMapping("/invoices/{id}/payments/{paymentId}")
    public ResponseEntity<ApiResponse<InvoiceView>> removePayment(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(fees.removePayment(org, id, paymentId)));
    }

    @PostMapping("/invoices/{id}/void")
    public ResponseEntity<ApiResponse<InvoiceView>> voidInvoice(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody VoidRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(fees.voidInvoice(org, id, in)));
    }
}
