package com.businessplatform.hr.payload;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.hr.payload.HrPayloads.Balance;
import com.businessplatform.hr.payload.HrPayloads.PersonRef;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Offboarding: resignations, departures HR starts, their checklists and the checklist template. */
public final class OffboardingPayloads {

    private OffboardingPayloads() {
    }

    public static final String KIND = "RESIGNATION|TERMINATION|END_OF_CONTRACT|RETIREMENT|OTHER";

    /** The person's own resignation: their proposed last day. */
    public record ResignRequest(@NotNull LocalDate lastDay, @Size(max = 1000) String reason) {
    }

    /** HR starts someone's departure ({@code noticeOn} empty = today). */
    public record StartRequest(@NotNull UUID userId, @NotNull @Pattern(regexp = KIND) String kind, LocalDate noticeOn,
            @NotNull LocalDate lastDay, @Size(max = 1000) String reason, Boolean rehire) {
    }

    /** Accepting a resignation, or changing an open offboarding. */
    public record UpdateRequest(@NotNull @Pattern(regexp = KIND) String kind, @NotNull LocalDate lastDay,
            @Size(max = 1000) String reason, Boolean rehire) {
    }

    public record ItemRequest(@NotBlank @Size(max = 200) String title, LocalDate dueDate) {
    }

    public record DoneRequest(boolean done) {
    }

    public record ItemView(UUID id, String title, LocalDate dueDate, boolean done, Instant doneAt, PersonRef doneBy) {
    }

    public record OffboardingSummary(UUID id, PersonRef person, String kind, String status, LocalDate noticeOn, LocalDate lastDay,
            int done, int total) {
    }

    /** What to hand over before they go: people reporting to them, approvals waiting on them, units they head. */
    public record Handover(List<PersonRef> reports, long waitingApprovals, List<String> unitsHeaded) {
    }

    /**
     * {@code leave}: their leave balances in the last day's year (unused paid leave is paid out); {@code canManage}:
     * HR may change it; {@code canCancel}: HR, or the person while it is only requested.
     */
    public record OffboardingDetail(OffboardingSummary summary, String reason, Boolean rehire, List<ItemView> items,
            Handover handover, List<Balance> leave, PersonRef requestedBy, PersonRef startedBy, PersonRef completedBy,
            Instant completedAt, boolean canManage, boolean canCancel) {
    }

    public record TemplateItem(@NotBlank @Size(max = 200) String title, @Min(-90) @Max(90) int dueDays) {
    }

    public record TemplateRequest(@NotNull @Size(max = 30) List<@Valid @NotNull TemplateItem> items) {
    }
}
