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

    public record StepInput(@NotNull @Pattern(regexp = "USER|ORG_ROLE|PROJECT_LEAD|MANAGER|SECOND_MANAGER") String type, UUID userId,
            @Pattern(regexp = "OWNER|ADMIN") String role) {
    }

    /** {@code remindAfterHours} / {@code escalateAfterHours}: remind the approver, then let Admins decide (null = never). */
    public record WorkflowRequest(@NotBlank @Size(min = 2, max = 120) String name,
            @NotBlank String moduleKey, @NotEmpty @Size(max = 5) List<@Valid StepInput> steps, Boolean active,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(720) Integer remindAfterHours,
            @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(2160) Integer escalateAfterHours) {
    }

    public record StepView(int order, String type, UUID userId, String userName, String role) {
    }

    public record WorkflowResponse(UUID id, String name, String moduleKey, boolean active, List<StepView> steps,
            Integer remindAfterHours, Integer escalateAfterHours) {
    }

    /** {@code userId}: whose approvals are handed over (empty = the caller's own; others need Admin or HR manager). */
    public record DelegationRequest(UUID userId, @jakarta.validation.constraints.NotNull UUID delegateId,
            @jakarta.validation.constraints.NotNull java.time.LocalDate startsOn,
            @jakarta.validation.constraints.NotNull java.time.LocalDate endsOn, @Size(max = 300) String reason) {
    }

    /** {@code active}: covers today. */
    public record DelegationView(UUID id, PersonView user, PersonView delegate, java.time.LocalDate startsOn,
            java.time.LocalDate endsOn, String reason, boolean active, boolean canRevoke) {
    }

    /** {@code mine}: handed over by me or to me; {@code all}: everyone's, for Admins and HR managers. */
    public record DelegationsView(List<DelegationView> mine, List<DelegationView> all, boolean canManage) {
    }

    public record SubmitRequest(@NotNull UUID workflowId, @NotBlank String moduleKey, @NotBlank String recordType,
            @NotNull UUID recordId, @Size(max = 5000) String summary) {
    }

    public record DecisionRequest(@Size(max = 5000) String comment) {
    }

    public record PersonView(UUID id, String name) {
    }

    /** {@code actingFor}: the approver a delegate decided for; {@code escalated}: Admins may decide too. */
    public record StepState(int order, PersonView approver, String role, String status, PersonView decidedBy,
            Instant decidedAt, PersonView actingFor, boolean escalated, PersonView delegate) {
    }

    public record ActionView(PersonView actor, String action, Integer step, String comment, Instant at, PersonView actingFor) {
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
