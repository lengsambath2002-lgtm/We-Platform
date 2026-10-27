package com.businessplatform.workflow.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Workflows (approval routes) and approval requests. */
public final class WorkflowPayloads {

    private WorkflowPayloads() {
    }

    public record StepInput(@NotNull @Pattern(regexp = "USER|ORG_ROLE|PROJECT_LEAD") String type, UUID userId,
            @Pattern(regexp = "OWNER|ADMIN") String role) {
    }

    public record WorkflowRequest(@NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank String moduleKey, @NotEmpty @Size(max = 5) List<@Valid StepInput> steps, Boolean active) {
    }

    public record StepView(int order, String type, UUID userId, String userName, String role) {
    }

    public record WorkflowResponse(UUID id, String name, String moduleKey, boolean active, List<StepView> steps) {
    }

    public record SubmitRequest(@NotNull UUID workflowId, @NotBlank String moduleKey, @NotBlank String recordType,
            @NotNull UUID recordId, @Size(max = 5000) String summary) {
    }

    public record DecisionRequest(@Size(max = 5000) String comment) {
    }

    public record PersonView(UUID id, String name) {
    }

    public record StepState(int order, PersonView approver, String role, String status, PersonView decidedBy,
            Instant decidedAt) {
    }

    public record ActionView(PersonView actor, String action, Integer step, String comment, Instant at) {
    }

    public record ApprovalSummary(UUID id, String title, String moduleKey, String recordType, UUID recordId,
            String recordLink, String status, int currentStep, int steps, PersonView requester, Instant createdAt,
            Instant updatedAt) {
    }

    /** {@code canDecide}: the caller is the current step's approver; {@code canResubmit}/{@code canWithdraw}: the requester. */
    public record ApprovalDetail(ApprovalSummary summary, String text, String workflowName, List<StepState> steps,
            List<ActionView> history, boolean canDecide, boolean canResubmit, boolean canWithdraw) {
    }
}
