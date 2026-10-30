package com.businessplatform.business.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.businessplatform.business.payload.BusinessPayloads.CustomerRef;
import com.businessplatform.business.payload.BusinessPayloads.PersonRef;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** General Business: the sales pipeline (leads and deals). Money is USD. */
public final class CrmPayloads {

    private CrmPayloads() {
    }

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** Either {@code customerId}, or a lead's {@code company} / {@code contactName}. {@code ownerId} null = me. */
    public record DealRequest(@NotBlank @Size(max = 200) String title, UUID customerId, @Size(max = 200) String company,
            @Size(max = 120) String contactName, @Email @Size(max = 160) String email,
            @Pattern(regexp = "^$|^[0-9+()\\-. ]{6,30}$", message = "Use digits, spaces and + ( ) - only") String phone,
            @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal value,
            @NotNull @Pattern(regexp = "REFERRAL|WEBSITE|SOCIAL|EVENT|COLD_CALL|OTHER") String source,
            LocalDate expectedClose, UUID ownerId, @Size(max = 300) String nextStep, LocalDate nextStepDate,
            @Size(max = 4000) String notes) {
    }

    /** LOST needs a reason. */
    public record DealStageRequest(@NotNull @Pattern(regexp = "LEAD|QUALIFIED|PROPOSAL|NEGOTIATION|WON|LOST") String stage,
            @Size(max = 500) String reason) {
    }

    public record DealActivityRequest(@NotNull @Pattern(regexp = "CALL|MEETING|EMAIL|NOTE") String kind,
            @NotBlank @Size(max = 4000) String body, LocalDate happenedOn) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record QuoteRef(UUID id, String number, String status) {
    }

    /** {@code probability}: percent by stage; {@code followUpDue}: the next step's date has come. */
    public record DealView(UUID id, String title, CustomerRef customer, String company, String contactName, String email,
            String phone, BigDecimal value, String stage, int probability, String source, LocalDate expectedClose,
            PersonRef owner, String nextStep, LocalDate nextStepDate, boolean followUpDue, QuoteRef quote,
            String lostReason, Instant closedAt, String notes, Instant createdAt, Instant updatedAt, boolean canEdit) {
    }

    public record DealActivityView(UUID id, String kind, String body, LocalDate happenedOn, PersonRef by, Instant createdAt) {
    }

    public record DealDetail(DealView deal, List<DealActivityView> activities) {
    }

    /** {@code weighted}: value × the stage's probability. */
    public record StageTotal(String stage, int count, BigDecimal value, BigDecimal weighted) {
    }

    /** {@code winRate}: of deals closed in the last 90 days, the share won (percent; null when none closed). */
    public record Pipeline(List<StageTotal> stages, BigDecimal openValue, BigDecimal weighted, BigDecimal wonThisMonth,
            int wonCountThisMonth, Integer winRate, List<DealView> deals) {
    }
}
