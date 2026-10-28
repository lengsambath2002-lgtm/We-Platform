package com.businessplatform.report.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.report.payload.ReportPayloads.*;
import com.businessplatform.report.service.ReportService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Reports. {@code from} / {@code to} are inclusive dates (default: the last 30 days); {@code project} is a
 * project key (default: every project the caller may see). CSV export is done by the web app from these same
 * responses, so a download always matches the table on screen.
 */
@Tag(name = "Reports")
@RestController
@RequestMapping("/api/orgs/{org}/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reports;

    @GetMapping("/task-flow")
    public ResponseEntity<ApiResponse<TaskFlow>> taskFlow(@PathVariable String org,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(ApiResponse.ok(reports.taskFlow(org, from, to, project)));
    }

    @GetMapping("/open-work")
    public ResponseEntity<ApiResponse<OpenWork>> openWork(@PathVariable String org,
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(ApiResponse.ok(reports.openWork(org, project)));
    }

    @GetMapping("/workload")
    public ResponseEntity<ApiResponse<Workload>> workload(@PathVariable String org,
            @RequestParam(required = false) String project) {
        return ResponseEntity.ok(ApiResponse.ok(reports.workload(org, project)));
    }

    @GetMapping("/approvals")
    public ResponseEntity<ApiResponse<Approvals>> approvals(@PathVariable String org,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String project, @RequestParam(required = false) Integer staleDays) {
        return ResponseEntity.ok(ApiResponse.ok(reports.approvals(org, from, to, project, staleDays)));
    }
}
