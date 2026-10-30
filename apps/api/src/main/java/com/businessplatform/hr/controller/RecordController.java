package com.businessplatform.hr.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;
import com.businessplatform.hr.payload.RecordPayloads.*;
import com.businessplatform.hr.service.RecordService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Employee records: personal details, contracts, documents, salary history and what expires soon. */
@RestController
@RequestMapping("/api/orgs/{org}/hr")
@RequiredArgsConstructor
@Tag(name = "HR records")
public class RecordController {

    private final RecordService records;
    private final FileService files;

    @GetMapping("/people/{userId}/records")
    public ResponseEntity<ApiResponse<RecordsView>> get(@PathVariable String org, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(records.get(org, userId)));
    }

    @PutMapping("/people/{userId}/personal")
    public ResponseEntity<ApiResponse<RecordsView>> personal(@PathVariable String org, @PathVariable UUID userId,
            @Valid @RequestBody PersonalRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(records.updatePersonal(org, userId, in)));
    }

    @PostMapping("/people/{userId}/contracts")
    public ResponseEntity<ApiResponse<RecordsView>> addContract(@PathVariable String org, @PathVariable UUID userId,
            @Valid @RequestBody ContractRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(records.addContract(org, userId, in)));
    }

    @PutMapping("/contracts/{id}")
    public ResponseEntity<ApiResponse<RecordsView>> updateContract(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ContractRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(records.updateContract(org, id, in)));
    }

    @DeleteMapping("/contracts/{id}")
    public ResponseEntity<ApiResponse<RecordsView>> deleteContract(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(records.deleteContract(org, id)));
    }

    @PostMapping(value = "/contracts/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RecordsView>> contractFile(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(records.attachContractFile(org, id, file)));
    }

    @GetMapping("/contracts/{id}/file")
    public ResponseEntity<byte[]> downloadContract(@PathVariable String org, @PathVariable UUID id) {
        return download(records.contractFile(org, id));
    }

    @PostMapping(value = "/people/{userId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<RecordsView>> addDocument(@PathVariable String org, @PathVariable UUID userId,
            @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "OTHER") String category,
            @RequestParam String title, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiresOn) {
        return ResponseEntity.status(201).body(ApiResponse.ok(records.addDocument(org, userId, category, title, expiresOn, file)));
    }

    @GetMapping("/documents/{id}/file")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String org, @PathVariable UUID id) {
        return download(records.documentFile(org, id));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<RecordsView>> deleteDocument(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(records.deleteDocument(org, id)));
    }

    @GetMapping("/payroll/people/{userId}/history")
    public ResponseEntity<ApiResponse<List<PayChangeView>>> payHistory(@PathVariable String org, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(records.payHistory(org, userId)));
    }

    /** Contracts, probations, IDs and documents ending within {@code days} (and up to 30 days ago). */
    @GetMapping("/records/expiring")
    public ResponseEntity<ApiResponse<List<ExpiringItem>>> expiring(@PathVariable String org, @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(ApiResponse.ok(records.expiring(org, days)));
    }

    private ResponseEntity<byte[]> download(StoredFile f) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(f));
    }
}
