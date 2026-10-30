package com.businessplatform.hr.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.hr.payload.OffboardingPayloads.*;
import com.businessplatform.hr.service.OffboardingService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Offboarding: resignations, departures, their checklists and the checklist template. */
@RestController
@RequestMapping("/api/orgs/{org}/hr/offboarding")
@RequiredArgsConstructor
@Tag(name = "HR offboarding")
public class OffboardingController {

    private final OffboardingService offboarding;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OffboardingSummary>>> list(@PathVariable String org,
            @RequestParam(defaultValue = "true") boolean open) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.list(org, open)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OffboardingDetail>> start(@PathVariable String org, @Valid @RequestBody StartRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(offboarding.start(org, in)));
    }

    /** The caller's own open resignation or departure (data is null without one). */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<OffboardingDetail>> mine(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.mine(org).orElse(null)));
    }

    @PostMapping("/resign")
    public ResponseEntity<ApiResponse<OffboardingDetail>> resign(@PathVariable String org, @Valid @RequestBody ResignRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(offboarding.resign(org, in)));
    }

    @GetMapping("/template")
    public ResponseEntity<ApiResponse<List<TemplateItem>>> template(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.template(org)));
    }

    @PutMapping("/template")
    public ResponseEntity<ApiResponse<List<TemplateItem>>> saveTemplate(@PathVariable String org, @Valid @RequestBody TemplateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.saveTemplate(org, in)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OffboardingDetail>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.get(org, id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OffboardingDetail>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.update(org, id, in)));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<OffboardingDetail>> accept(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UpdateRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.accept(org, id, in)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OffboardingDetail>> cancel(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.cancel(org, id)));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<OffboardingDetail>> complete(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.complete(org, id)));
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<OffboardingDetail>> addItem(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ItemRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.addItem(org, id, in)));
    }

    @PutMapping("/items/{itemId}/done")
    public ResponseEntity<ApiResponse<OffboardingDetail>> done(@PathVariable String org, @PathVariable UUID itemId,
            @RequestBody DoneRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.setDone(org, itemId, in.done())));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<OffboardingDetail>> removeItem(@PathVariable String org, @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.ok(offboarding.removeItem(org, itemId)));
    }
}
