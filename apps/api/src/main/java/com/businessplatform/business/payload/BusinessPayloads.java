package com.businessplatform.business.payload;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** General Business: settings, customers, products, quotes, invoices, payments and expenses. Money is USD. */
public final class BusinessPayloads {

    private BusinessPayloads() {
    }

    private static final String PHONE = "^$|^[0-9+()\\-. ]{6,30}$";
    private static final String PHONE_MESSAGE = "Use digits, spaces and + ( ) - only";
    private static final String CENTS = "Use at most 2 decimals";

    // ── Requests ─────────────────────────────────────────────────────────────────────

    /** Empty or null clears a text field. */
    public record SettingsRequest(@Size(max = 200) String legalName, @Size(max = 500) String address,
            @Size(max = 40) String taxId, @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @Email @Size(max = 160) String email, @Size(max = 2000) String invoiceFooter,
            @NotNull @Min(0) @Max(365) Integer paymentTermsDays, boolean vatDefault) {
    }

    public record CustomerRequest(@NotNull @Pattern(regexp = "COMPANY|INDIVIDUAL") String kind,
            @NotBlank @Size(max = 200) String name, @Size(max = 120) String contactName,
            @Email @Size(max = 160) String email, @Pattern(regexp = PHONE, message = PHONE_MESSAGE) String phone,
            @Size(max = 500) String address, @Size(max = 40) String taxId, @Size(max = 2000) String notes) {
    }

    public record ProductRequest(@NotBlank @Size(max = 160) String nameEn, @Size(max = 160) String nameKm,
            @NotBlank @Size(max = 20) String unit,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal unitPrice,
            boolean vat, Boolean active) {
    }

    /** A line: a product (its name fills an empty description) or free text. {@code discountPct} null = 0. */
    public record LineInput(UUID productId, @Size(max = 500) String description, @Size(max = 20) String unit,
            @NotNull @DecimalMin(value = "0.001") @DecimalMax("999999") @Digits(integer = 6, fraction = 3, message = "Use at most 3 decimals") BigDecimal quantity,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal unitPrice,
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2, message = CENTS) BigDecimal discountPct,
            boolean vat) {
    }

    /** {@code vatEnabled} null = the organisation's default. */
    public record QuoteRequest(@NotNull UUID customerId, @NotNull LocalDate issueDate, LocalDate validUntil,
            Boolean vatEnabled, @NotEmpty @Size(max = 100) List<@Valid @NotNull LineInput> lines,
            @Size(max = 4000) String notes) {
    }

    /** Dates may stay empty on a draft: issuing fills today and today + the payment terms. */
    public record InvoiceRequest(@NotNull UUID customerId, LocalDate issueDate, LocalDate dueDate, Boolean vatEnabled,
            @NotEmpty @Size(max = 100) List<@Valid @NotNull LineInput> lines, @Size(max = 4000) String notes) {
    }

    public record PaymentRequest(@NotNull LocalDate paidOn,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal amount,
            @NotNull @Pattern(regexp = "CASH|BANK_TRANSFER|KHQR|CARD|OTHER") String method,
            @Size(max = 120) String reference) {
    }

    public record VoidRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record CategoryRequest(@NotBlank @Size(max = 60) String nameEn, @NotBlank @Size(max = 60) String nameKm,
            Boolean active) {
    }

    /** {@code submit}: send it for approval right away (otherwise it stays a draft). */
    public record ExpenseRequest(@NotNull LocalDate spentOn, @NotNull UUID categoryId, @Size(max = 160) String supplier,
            @NotNull @DecimalMin("0.01") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal amount,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2, message = CENTS) BigDecimal vatAmount,
            @Size(max = 2000) String notes, boolean submit) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    public record SettingsView(String legalName, String address, String taxId, String phone, String email,
            String invoiceFooter, int paymentTermsDays, boolean vatDefault, BigDecimal vatRate, String orgName) {
    }

    public record CustomerRef(UUID id, String name, boolean archived) {
    }

    /** {@code balance}: what the customer owes on issued invoices. */
    public record CustomerView(UUID id, String kind, String name, String contactName, String email, String phone,
            String address, String taxId, String notes, boolean archived, BigDecimal balance, Instant createdAt) {
    }

    /** A customer's page: details, totals and their recent documents. */
    public record CustomerDetail(CustomerView customer, BigDecimal overdue, BigDecimal invoiced, BigDecimal received,
            List<QuoteSummary> quotes, List<InvoiceSummary> invoices, boolean canArchive) {
    }

    public record ProductView(UUID id, String nameEn, String nameKm, String unit, BigDecimal unitPrice, boolean vat,
            boolean active) {
    }

    public record LineView(UUID id, int position, UUID productId, String description, String unit,
            BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct, boolean vat, BigDecimal discount,
            BigDecimal net) {
    }

    public record QuoteSummary(UUID id, String number, CustomerRef customer, String status, boolean expired,
            LocalDate issueDate, LocalDate validUntil, BigDecimal total, UUID invoiceId, PersonRef createdBy) {
    }

    /** {@code can*}: what the caller may do now (the server checks again). */
    public record QuoteView(UUID id, String number, CustomerRef customer, String status, boolean expired,
            LocalDate issueDate, LocalDate validUntil, boolean vatEnabled, BigDecimal vatRate, List<LineView> lines,
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal vatTotal, BigDecimal total, String notes,
            UUID invoiceId, String invoiceNumber, PersonRef createdBy, Instant createdAt, boolean canEdit,
            boolean canSend, boolean canDecide, boolean canConvert, boolean canDelete) {
    }

    /** {@code overdue}: issued, unpaid and past its due date. */
    public record InvoiceSummary(UUID id, String number, CustomerRef customer, String status, boolean overdue,
            LocalDate issueDate, LocalDate dueDate, BigDecimal total, BigDecimal paid, BigDecimal balance,
            PersonRef createdBy) {
    }

    public record PaymentView(UUID id, LocalDate paidOn, BigDecimal amount, String method, String reference,
            PersonRef recordedBy, Instant createdAt) {
    }

    public record InvoiceView(UUID id, String number, CustomerView customer, String status, boolean overdue,
            LocalDate issueDate, LocalDate dueDate, boolean vatEnabled, BigDecimal vatRate, List<LineView> lines,
            BigDecimal subtotal, BigDecimal discountTotal, BigDecimal vatTotal, BigDecimal total, BigDecimal paid,
            BigDecimal balance, String notes, UUID quoteId, String quoteNumber, List<PaymentView> payments,
            String voidReason, Instant voidedAt, PersonRef createdBy, Instant createdAt, Instant issuedAt,
            boolean canEdit, boolean canIssue, boolean canPay, boolean canVoid, boolean canDelete,
            boolean canRemovePayments) {
    }

    public record CategoryView(UUID id, String nameEn, String nameKm, boolean active) {
    }

    public record FileRef(UUID id, String name, String contentType, long size) {
    }

    /**
     * {@code approvalStatus}: the engine's status of its approval (PENDING, CHANGES_REQUESTED …), null when none.
     */
    public record ExpenseView(UUID id, PersonRef person, LocalDate spentOn, CategoryView category, String supplier,
            BigDecimal amount, BigDecimal vatAmount, String notes, FileRef receipt, String status, UUID approvalId,
            String approvalStatus, Instant paidAt, Instant createdAt, boolean canEdit, boolean canSubmit,
            boolean canDelete, boolean canMarkPaid) {
    }

    public record ActivityView(UUID id, PersonRef actor, String verb, java.util.Map<String, Object> data, Instant at) {
    }

    // ── Overview ─────────────────────────────────────────────────────────────────────

    /** One month (first day) of issued sales and approved expenses; {@code expenses} null for non-admins. */
    public record MonthTotals(LocalDate month, BigDecimal sales, BigDecimal expenses) {
    }

    /**
     * The business home. Expense totals ({@code expensesThisMonth}, each month's {@code expenses}) only for
     * admins (null otherwise); {@code waitingExpenses} are everyone's for admins, the caller's own otherwise.
     */
    public record Overview(BigDecimal receivable, int openInvoices, BigDecimal overdue, int overdueInvoices,
            BigDecimal salesThisMonth, BigDecimal expensesThisMonth, List<MonthTotals> months,
            List<InvoiceSummary> recentInvoices, List<InvoiceSummary> dueSoon, List<ExpenseView> waitingExpenses) {
    }
}
