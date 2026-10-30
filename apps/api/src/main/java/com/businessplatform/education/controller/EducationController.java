package com.businessplatform.education.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.education.payload.EducationPayloads.*;
import com.businessplatform.education.service.EduOverviewService;
import com.businessplatform.education.service.EduReportService;
import com.businessplatform.education.service.GuardianService;
import com.businessplatform.education.service.StructureService;
import com.businessplatform.education.service.StudentService;
import com.businessplatform.education.service.TeacherService;
import com.businessplatform.education.service.WithdrawalService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Education: the home, reports, academic structure and settings, teachers, parents / guardians, students and
 * withdrawals. Who may do what is described on {@code EducationAccess}; lists are paged where they can grow
 * ({@code page} from 0, {@code size} up to 100).
 */
@RestController
@RequestMapping("/api/orgs/{org}/education")
@RequiredArgsConstructor
@Tag(name = "Education")
public class EducationController {

    private final EduOverviewService overview;
    private final EduReportService reports;
    private final StructureService structure;
    private final TeacherService teachers;
    private final GuardianService guardians;
    private final StudentService students;
    private final WithdrawalService withdrawals;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Overview>> overview(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(overview.overview(org)));
    }

    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Reports>> reports(@PathVariable String org,
            @RequestParam(required = false) java.time.LocalDate from, @RequestParam(required = false) java.time.LocalDate to,
            @RequestParam(required = false) UUID termId) {
        return ResponseEntity.ok(ApiResponse.ok(reports.reports(org, from, to, termId)));
    }

    // ── Settings and academic structure ──────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(structure.getSettings(org)));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@PathVariable String org, @Valid @RequestBody SettingsRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.updateSettings(org, in)));
    }

    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<YearView>>> years(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(structure.years(org)));
    }

    @PostMapping("/years")
    public ResponseEntity<ApiResponse<YearView>> createYear(@PathVariable String org, @Valid @RequestBody YearRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.saveYear(org, null, in)));
    }

    @PutMapping("/years/{id}")
    public ResponseEntity<ApiResponse<YearView>> updateYear(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody YearRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.saveYear(org, id, in)));
    }

    @PostMapping("/years/{id}/terms")
    public ResponseEntity<ApiResponse<TermView>> createTerm(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody TermRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.saveTerm(org, id, null, in)));
    }

    @PutMapping("/terms/{id}")
    public ResponseEntity<ApiResponse<TermView>> updateTerm(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody TermRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.saveTerm(org, null, id, in)));
    }

    @DeleteMapping("/terms/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTerm(@PathVariable String org, @PathVariable UUID id) {
        structure.deleteTerm(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/grades")
    public ResponseEntity<ApiResponse<List<GradeView>>> grades(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(structure.grades(org)));
    }

    @PostMapping("/grades")
    public ResponseEntity<ApiResponse<GradeView>> createGrade(@PathVariable String org, @Valid @RequestBody GradeRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.saveGrade(org, null, in)));
    }

    @PutMapping("/grades/{id}")
    public ResponseEntity<ApiResponse<GradeView>> updateGrade(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody GradeRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.saveGrade(org, id, in)));
    }

    @GetMapping("/subjects")
    public ResponseEntity<ApiResponse<List<SubjectView>>> subjects(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(structure.subjects(org)));
    }

    @PostMapping("/subjects")
    public ResponseEntity<ApiResponse<SubjectView>> createSubject(@PathVariable String org, @Valid @RequestBody SubjectRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(structure.saveSubject(org, null, in)));
    }

    @PutMapping("/subjects/{id}")
    public ResponseEntity<ApiResponse<SubjectView>> updateSubject(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody SubjectRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(structure.saveSubject(org, id, in)));
    }

    // ── Teachers ─────────────────────────────────────────────────────────────────────

    @GetMapping("/teachers")
    public ResponseEntity<ApiResponse<List<TeacherView>>> teachers(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(teachers.list(org)));
    }

    @PostMapping("/teachers")
    public ResponseEntity<ApiResponse<TeacherView>> addTeacher(@PathVariable String org, @Valid @RequestBody TeacherRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(teachers.save(org, null, in)));
    }

    @PutMapping("/teachers/{id}")
    public ResponseEntity<ApiResponse<TeacherView>> updateTeacher(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody TeacherRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(teachers.save(org, id, in)));
    }

    // ── Parents and guardians ────────────────────────────────────────────────────────

    @GetMapping("/guardians")
    public ResponseEntity<ApiResponse<PageResponse<GuardianView>>> guardians(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(guardians.list(org, q, page, size)));
    }

    @PostMapping("/guardians")
    public ResponseEntity<ApiResponse<GuardianView>> createGuardian(@PathVariable String org, @Valid @RequestBody GuardianRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(guardians.save(org, null, in)));
    }

    @GetMapping("/guardians/{id}")
    public ResponseEntity<ApiResponse<GuardianView>> guardian(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(guardians.get(org, id)));
    }

    @PutMapping("/guardians/{id}")
    public ResponseEntity<ApiResponse<GuardianView>> updateGuardian(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody GuardianRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(guardians.save(org, id, in)));
    }

    // ── Students ─────────────────────────────────────────────────────────────────────

    @GetMapping("/students")
    public ResponseEntity<ApiResponse<PageResponse<StudentSummary>>> students(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID classId, @RequestParam(required = false) UUID gradeId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(students.list(org, q, status, classId, gradeId, page, size)));
    }

    @PostMapping("/students")
    public ResponseEntity<ApiResponse<StudentDetail>> register(@PathVariable String org, @Valid @RequestBody RegisterRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(students.register(org, in)));
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<ApiResponse<StudentDetail>> student(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(students.get(org, id)));
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<ApiResponse<StudentDetail>> updateStudent(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody StudentRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(students.update(org, id, in)));
    }

    @GetMapping("/students/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityView>>> studentHistory(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(students.history(org, id)));
    }

    @PostMapping("/students/{id}/guardians")
    public ResponseEntity<ApiResponse<StudentDetail>> addGuardian(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody GuardianLink in) {
        return ResponseEntity.ok(ApiResponse.ok(students.addGuardian(org, id, in)));
    }

    @DeleteMapping("/students/{id}/guardians/{guardianId}")
    public ResponseEntity<ApiResponse<StudentDetail>> removeGuardian(@PathVariable String org, @PathVariable UUID id, @PathVariable UUID guardianId) {
        return ResponseEntity.ok(ApiResponse.ok(students.removeGuardian(org, id, guardianId)));
    }

    @PostMapping("/students/{id}/graduate")
    public ResponseEntity<ApiResponse<StudentDetail>> graduate(@PathVariable String org, @PathVariable UUID id, @Valid @RequestBody GraduateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(students.graduate(org, id, in)));
    }

    @PostMapping("/students/{id}/withdrawal")
    public ResponseEntity<ApiResponse<WithdrawalView>> requestWithdrawal(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody WithdrawalRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(withdrawals.request(org, id, in)));
    }
}
