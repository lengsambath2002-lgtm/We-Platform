package com.businessplatform.billing.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SaaS plans and subscriptions. Prices are USD per member (not guests) per month or year. */
public final class BillingPayloads {

    private BillingPayloads() {
    }

    public record PlanView(String code, String nameEn, String nameKm, BigDecimal priceMonthly, BigDecimal priceYearly,
            Integer maxMembers, int storageGb, List<String> modules, int trialDays, boolean publicPlan, int sortOrder) {
    }

    /** {@code members}: active members who aren't guests (what the plan counts and charges). */
    public record Usage(long members, Integer maxMembers, long storageBytes, long storageLimitBytes) {
    }

    /**
     * {@code nextAmount}: what the next period costs at today's members (0 when free or complimentary);
     * {@code canManage}: Owners and members with the billing role may change the plan.
     */
    public record SubscriptionView(PlanView plan, String status, String billingCycle, Instant trialEndsAt,
            Instant currentPeriodEnd, boolean cancelAtPeriodEnd, Usage usage, BigDecimal nextAmount, List<PlanView> plans,
            boolean canManage) {
    }

    public record ChangeRequest(@NotBlank @Size(max = 20) String planCode, @NotNull @Pattern(regexp = "MONTHLY|YEARLY") String cycle) {
    }

    /** The platform's plan catalogue (super admins). */
    public record PlanRequest(@NotBlank @Size(max = 60) String nameEn, @NotBlank @Size(max = 60) String nameKm,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal priceMonthly,
            @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal priceYearly,
            @Min(1) Integer maxMembers, @Min(0) @Max(100000) int storageGb, @NotNull @Size(max = 20) List<@NotBlank String> modules,
            @Min(0) @Max(90) int trialDays, boolean publicPlan, @Min(0) @Max(1000) int sortOrder) {
    }

    /** A super admin sets an organisation's subscription (a contract, a complimentary plan, a longer trial …). */
    public record AdminSubscriptionRequest(@NotBlank @Size(max = 20) String planCode,
            @NotNull @Pattern(regexp = "TRIALING|ACTIVE|PAST_DUE|CANCELLED|COMPLIMENTARY") String status,
            @NotNull @Pattern(regexp = "MONTHLY|YEARLY") String cycle, Instant trialEndsAt, Instant currentPeriodEnd,
            @Size(max = 500) String note) {
    }

    /** For the platform's organisation list and details. */
    public record SubscriptionBrief(UUID orgId, String planCode, String status, String billingCycle, Instant trialEndsAt,
            Instant currentPeriodEnd, String note) {
    }

    /** How to pay: the seller and bank on invoices (empty fields are not set up). */
    public record PayTo(String sellerName, String sellerAddress, String sellerTaxId, String bankName, String bankAccountName,
            String bankAccountNumber, boolean khqr) {
    }

    /**
     * {@code khqr}: the KHQR string to show as a QR code (scanned by any Cambodian banking app), when set up;
     * {@code overdue}: open and past its due date; {@code claimed}: the organisation said it paid.
     */
    public record InvoiceView(UUID id, UUID orgId, String orgName, String number, String planCode, String billingCycle, int seats,
            BigDecimal unitPrice, BigDecimal amount, Instant periodStart, Instant periodEnd, String status, Instant issuedAt,
            Instant dueAt, boolean overdue, String khqr, Instant claimedAt, String claimReference, Instant paidAt,
            String paymentMethod, String paymentReference, String voidReason, PayTo payTo) {
    }

    /** The organisation says it paid (bank transfer or KHQR), with the reference the bank gave. */
    public record ClaimRequest(@NotBlank @Size(max = 120) String reference) {
    }

    /** The platform records a payment it received. */
    public record PaymentRequest(@NotNull @Pattern(regexp = "BANK_TRANSFER|KHQR|ABA_PAYWAY|CASH|OTHER") String method,
            @Size(max = 120) String reference) {
    }

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {
    }
}
