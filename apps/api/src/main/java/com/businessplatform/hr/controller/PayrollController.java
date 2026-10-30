package com.businessplatform.hr.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.error.BusinessException;
import com.businessplatform.hr.payload.PayrollPayloads.*;
import com.businessplatform.hr.service.PayrollService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: payroll (HR managers), and each person's own paid payslips. */
@RestController
@RequestMapping("/api/orgs/{org}/hr/payroll")
@RequiredArgsConstructor
@Tag(name = "HR payroll")
public class PayrollController {

    private final PayrollService payroll;

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.settings(org)));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@PathVariable String org,
            @Valid @RequestBody SettingsRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.updateSettings(org, in)));
    }

    @GetMapping("/people")
    public ResponseEntity<ApiResponse<List<PayView>>> people(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.people(org)));
    }

    @PutMapping("/people/{userId}")
    public ResponseEntity<ApiResponse<PayView>> setPay(@PathVariable String org, @PathVariable UUID userId,
            @Valid @RequestBody PayRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.setPay(org, userId, in)));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<RunSummary>>> runs(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.runs(org)));
    }

    @PostMapping("/runs")
    public ResponseEntity<ApiResponse<RunDetail>> create(@PathVariable String org, @Valid @RequestBody RunRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(payroll.create(org, in)));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<ApiResponse<RunDetail>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.get(org, id)));
    }

    @PutMapping("/runs/{id}")
    public ResponseEntity<ApiResponse<RunDetail>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody RunUpdate in) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.update(org, id, in)));
    }

    @DeleteMapping("/runs/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        payroll.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** {@code action}: recalculate, approve, reopen or pay. */
    @PostMapping("/runs/{id}/{action}")
    public ResponseEntity<ApiResponse<RunDetail>> act(@PathVariable String org, @PathVariable UUID id,
            @PathVariable String action) {
        return ResponseEntity.ok(ApiResponse.ok(switch (action) {
            case "recalculate" -> payroll.recalculate(org, id);
            case "approve" -> payroll.approve(org, id);
            case "reopen" -> payroll.reopen(org, id);
            case "pay" -> payroll.markPaid(org, id);
            default -> throw BusinessException.notFound();
        }));
    }

    @GetMapping(value = "/runs/{id}/export.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payroll.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + payroll.csv(org, id)).getBytes(StandardCharsets.UTF_8));
    }

    @PutMapping("/payslips/{id}/adjustments")
    public ResponseEntity<ApiResponse<RunDetail>> adjust(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody AdjustmentsRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.setAdjustments(org, id, in)));
    }

    @GetMapping("/payslips/mine")
    public ResponseEntity<ApiResponse<List<PayslipView>>> mine(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.mine(org)));
    }

    @GetMapping("/payslips/{id}")
    public ResponseEntity<ApiResponse<PayslipView>> payslip(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(payroll.payslip(org, id)));
    }
}
