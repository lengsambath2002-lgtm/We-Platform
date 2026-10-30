package com.businessplatform.hr.controller;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.YearMonth;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.hr.payload.InsightPayloads.ChartNode;
import com.businessplatform.hr.payload.InsightPayloads.Dashboard;
import com.businessplatform.hr.service.InsightService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** HR & Workforce: the HR dashboard, the org chart and the HR reports (CSV). */
@RestController
@RequestMapping("/api/orgs/{org}/hr")
@RequiredArgsConstructor
@Tag(name = "HR insights")
public class InsightController {

    private final InsightService insights;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Dashboard>> dashboard(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(insights.dashboard(org)));
    }

    @GetMapping("/org-chart")
    public ResponseEntity<ApiResponse<List<ChartNode>>> orgChart(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(insights.orgChart(org)));
    }

    @GetMapping("/reports/people.csv")
    public ResponseEntity<byte[]> people(@PathVariable String org) {
        return csv("employees.csv", insights.peopleCsv(org));
    }

    @GetMapping("/reports/attendance.csv")
    public ResponseEntity<byte[]> attendance(@PathVariable String org, @RequestParam YearMonth month) {
        return csv("attendance-" + month + ".csv", insights.attendanceCsv(org, month));
    }

    @GetMapping("/reports/leave.csv")
    public ResponseEntity<byte[]> leave(@PathVariable String org, @RequestParam int year) {
        return csv("leave-" + year + ".csv", insights.leaveCsv(org, Year.of(year)));
    }

    /** UTF-8 with a byte-order mark, so spreadsheets read Khmer names correctly. */
    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + body).getBytes(StandardCharsets.UTF_8));
    }
}
