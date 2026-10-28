package com.businessplatform.hr.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.hr.payload.HrPayloads.*;
import com.businessplatform.hr.service.BalanceService;
import com.businessplatform.hr.service.LeaveService;
import com.businessplatform.hr.service.LeaveTypeService;
import com.businessplatform.hr.service.ProfileService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: people directory and profiles, leave types, balances and leave requests. */
@RestController
@RequestMapping("/api/orgs/{org}/hr")
@RequiredArgsConstructor
@Tag(name = "HR")
public class HrController {

    private final ProfileService profiles;
    private final LeaveTypeService types;
    private final BalanceService balances;
    private final LeaveService leave;

    // ── People ───────────────────────────────────────────────────────────────────────

    @GetMapping("/people")
    public ResponseEntity<ApiResponse<List<PersonSummary>>> directory(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(profiles.directory(org)));
    }

    @GetMapping("/people/{userId}")
    public ResponseEntity<ApiResponse<ProfileView>> profile(@PathVariable String org, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(profiles.get(org, userId)));
    }

    /** HR managers: the whole profile. */
    @PutMapping("/people/{userId}")
    public ResponseEntity<ApiResponse<ProfileView>> updateProfile(@PathVariable String org, @PathVariable UUID userId,
            @Valid @RequestBody ProfileRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(profiles.update(org, userId, in)));
    }

    /** Anyone: their own phone and emergency contact. */
    @PutMapping("/me/contact")
    public ResponseEntity<ApiResponse<ProfileView>> updateContact(@PathVariable String org,
            @Valid @RequestBody ContactRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(profiles.updateContact(org, in)));
    }

    // ── Leave types and balances ─────────────────────────────────────────────────────

    @GetMapping("/leave-types")
    public ResponseEntity<ApiResponse<List<LeaveTypeView>>> leaveTypes(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(types.list(org)));
    }

    @PostMapping("/leave-types")
    public ResponseEntity<ApiResponse<LeaveTypeView>> createLeaveType(@PathVariable String org,
            @Valid @RequestBody LeaveTypeRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(types.save(org, null, in)));
    }

    @PutMapping("/leave-types/{id}")
    public ResponseEntity<ApiResponse<LeaveTypeView>> updateLeaveType(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody LeaveTypeRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(types.save(org, id, in)));
    }

    /** Without {@code userId}, the caller's; {@code year} defaults to this year. */
    @GetMapping("/balances")
    public ResponseEntity<ApiResponse<List<Balance>>> balances(@PathVariable String org,
            @RequestParam(required = false) UUID userId, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(balances.balances(org, userId, year)));
    }

    @GetMapping("/balances/{userId}/adjustments")
    public ResponseEntity<ApiResponse<List<AdjustmentView>>> adjustments(@PathVariable String org,
            @PathVariable UUID userId, @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.ok(balances.adjustments(org, userId, year)));
    }

    @PostMapping("/balances/adjust")
    public ResponseEntity<ApiResponse<List<Balance>>> adjust(@PathVariable String org, @Valid @RequestBody AdjustRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(balances.adjust(org, in)));
    }

    // ── Leave requests ───────────────────────────────────────────────────────────────

    @GetMapping("/leave/mine")
    public ResponseEntity<ApiResponse<List<LeaveView>>> mine(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(leave.mine(org)));
    }

    /** HR managers: everyone's requests. */
    @GetMapping("/leave")
    public ResponseEntity<ApiResponse<List<LeaveView>>> all(@PathVariable String org,
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID typeId, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.ok(leave.all(org, status, userId, typeId, year)));
    }

    @PostMapping("/leave")
    public ResponseEntity<ApiResponse<LeaveView>> request(@PathVariable String org, @Valid @RequestBody LeaveInput in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(leave.create(org, in)));
    }

    @GetMapping("/leave/{id}")
    public ResponseEntity<ApiResponse<LeaveView>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(leave.get(org, id)));
    }

    @PostMapping("/leave/{id}/withdraw")
    public ResponseEntity<ApiResponse<LeaveView>> withdraw(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(leave.withdraw(org, id)));
    }

    @PostMapping("/leave/{id}/cancel")
    public ResponseEntity<ApiResponse<LeaveView>> cancel(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody(required = false) CancelRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(leave.cancel(org, id, in)));
    }

    /** Who is away (approved leave) on some day of {@code from}–{@code to}, at most 62 days. */
    @GetMapping("/absences")
    public ResponseEntity<ApiResponse<List<Absence>>> absences(@PathVariable String org,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(leave.absences(org, from, to)));
    }
}
