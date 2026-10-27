package com.businessplatform.workflow.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.workflow.payload.WorkflowPayloads.*;
import com.businessplatform.workflow.service.ApprovalService;
import com.businessplatform.workflow.service.WorkflowService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Workflows (approval routes) and approval requests for any module's records. */
@Tag(name = "Approvals")
@RestController
@RequestMapping("/api/orgs/{org}")
@RequiredArgsConstructor
public class ApprovalController {

    private final WorkflowService workflows;
    private final ApprovalService approvals;

    @GetMapping("/workflows")
    public ResponseEntity<ApiResponse<List<WorkflowResponse>>> workflows(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(workflows.list(org)));
    }

    @PostMapping("/workflows")
    public ResponseEntity<ApiResponse<WorkflowResponse>> createWorkflow(@PathVariable String org,
            @Valid @RequestBody WorkflowRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(workflows.save(org, null, in)));
    }

    @PutMapping("/workflows/{id}")
    public ResponseEntity<ApiResponse<WorkflowResponse>> updateWorkflow(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody WorkflowRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(workflows.save(org, id, in)));
    }

    @GetMapping("/approvals")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> list(@PathVariable String org,
            @RequestParam(defaultValue = "inbox") String box) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.list(org, box)));
    }

    @PostMapping("/approvals")
    public ResponseEntity<ApiResponse<ApprovalDetail>> submit(@PathVariable String org,
            @Valid @RequestBody SubmitRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(approvals.submit(org, in)));
    }

    @GetMapping("/approvals/{id}")
    public ResponseEntity<ApiResponse<ApprovalDetail>> get(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.get(org, id)));
    }

    @GetMapping("/approvals/record/{module}/{recordType}/{recordId}")
    public ResponseEntity<ApiResponse<List<ApprovalSummary>>> forRecord(@PathVariable String org,
            @PathVariable String module, @PathVariable String recordType, @PathVariable UUID recordId) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.forRecord(org, module, recordType, recordId)));
    }

    @PostMapping("/approvals/{id}/approve")
    public ResponseEntity<ApiResponse<ApprovalDetail>> approve(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.approve(org, id, in)));
    }

    @PostMapping("/approvals/{id}/reject")
    public ResponseEntity<ApiResponse<ApprovalDetail>> reject(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.reject(org, id, in)));
    }

    @PostMapping("/approvals/{id}/request-changes")
    public ResponseEntity<ApiResponse<ApprovalDetail>> requestChanges(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody DecisionRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.requestChanges(org, id, in)));
    }

    @PostMapping("/approvals/{id}/resubmit")
    public ResponseEntity<ApiResponse<ApprovalDetail>> resubmit(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody(required = false) DecisionRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.resubmit(org, id, in)));
    }

    @PostMapping("/approvals/{id}/withdraw")
    public ResponseEntity<ApiResponse<ApprovalDetail>> withdraw(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(approvals.withdraw(org, id)));
    }
}
