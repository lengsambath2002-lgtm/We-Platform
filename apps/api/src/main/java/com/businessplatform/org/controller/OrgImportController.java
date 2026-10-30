package com.businessplatform.org.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.org.payload.ImportPayloads.ImportRequest;
import com.businessplatform.org.payload.ImportPayloads.ImportResult;
import com.businessplatform.org.service.OrgImportService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Bulk import of people and the structure (Owners and Admins): check first, then apply all at once. */
@Tag(name = "People import")
@RestController
@RequestMapping("/api/orgs/{org}/people-import")
@RequiredArgsConstructor
public class OrgImportController {

    private final OrgImportService imports;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<ImportResult>> preview(@PathVariable String org, @Valid @RequestBody ImportRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(imports.preview(org, in)));
    }

    /** Applies nothing (and answers {@code applied: false}) while any row has a problem. */
    @PostMapping
    public ResponseEntity<ApiResponse<ImportResult>> apply(@PathVariable String org, @Valid @RequestBody ImportRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(imports.apply(org, in)));
    }
}
