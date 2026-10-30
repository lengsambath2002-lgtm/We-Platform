package com.businessplatform.business.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.business.payload.BusinessPayloads.QuoteView;
import com.businessplatform.business.payload.CrmPayloads.*;
import com.businessplatform.business.payload.FinancePayloads.Report;
import com.businessplatform.business.service.DealService;
import com.businessplatform.business.service.FinanceService;
import com.businessplatform.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** General Business: the sales pipeline (deals) and the finance reports. */
@RestController
@RequestMapping("/api/orgs/{org}/business")
@RequiredArgsConstructor
@Tag(name = "Business CRM and reports")
public class CrmController {

    private final DealService deals;
    private final FinanceService finance;

    @GetMapping("/deals")
    public ResponseEntity<ApiResponse<Pipeline>> pipeline(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(deals.pipeline(org)));
    }

    @PostMapping("/deals")
    public ResponseEntity<ApiResponse<DealDetail>> create(@PathVariable String org, @Valid @RequestBody DealRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(deals.create(org, in)));
    }

    @GetMapping("/deals/{id}")
    public ResponseEntity<ApiResponse<DealDetail>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(deals.get(org, id)));
    }

    @PutMapping("/deals/{id}")
    public ResponseEntity<ApiResponse<DealDetail>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody DealRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(deals.update(org, id, in)));
    }

    @DeleteMapping("/deals/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        deals.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/deals/{id}/stage")
    public ResponseEntity<ApiResponse<DealDetail>> move(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody DealStageRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(deals.move(org, id, in)));
    }

    @PostMapping("/deals/{id}/activities")
    public ResponseEntity<ApiResponse<DealDetail>> addActivity(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody DealActivityRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(deals.addActivity(org, id, in)));
    }

    @PostMapping("/deals/{id}/convert")
    public ResponseEntity<ApiResponse<DealDetail>> convert(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(deals.convert(org, id)));
    }

    @PostMapping("/deals/{id}/quote")
    public ResponseEntity<ApiResponse<QuoteView>> quote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.status(201).body(ApiResponse.ok(deals.startQuote(org, id)));
    }

    /** Owners and Admins. */
    @GetMapping("/reports")
    public ResponseEntity<ApiResponse<Report>> report(@PathVariable String org, @RequestParam int year) {
        return ResponseEntity.ok(ApiResponse.ok(finance.report(org, year)));
    }

    /** {@code which}: months, aging, customers or expenses. */
    @GetMapping("/reports/{which}.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String org, @PathVariable String which, @RequestParam int year) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + which + "-" + year + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(("﻿" + finance.csv(org, year, which)).getBytes(StandardCharsets.UTF_8));
    }
}
