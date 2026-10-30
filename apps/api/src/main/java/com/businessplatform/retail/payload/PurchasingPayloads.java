package com.businessplatform.retail.payload;

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

/** Retail &amp; Commerce: suppliers and purchase orders. Money is USD. */
public final class PurchasingPayloads {

    private PurchasingPayloads() {
    }

    // ── Requests ─────────────────────────────────────────────────────────────────────

    public record SupplierRequest(@NotBlank @Size(max = 200) String name, @Size(max = 120) String contactName,
            @Email @Size(max = 160) String email,
            @Pattern(regexp = "^$|^[0-9+()\\-. ]{6,30}$", message = "Use digits, spaces and + ( ) - only") String phone,
            @Size(max = 500) String address, @Size(max = 40) String taxId, @Min(0) @Max(365) int paymentTermsDays,
            @Size(max = 2000) String notes) {
    }

    public record LineInput(@NotBlank @Size(max = 300) String description, @Size(max = 60) String sku,
            @NotNull @DecimalMin("0.001") @DecimalMax("999999") @Digits(integer = 6, fraction = 3) BigDecimal quantity,
            @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2) BigDecimal unitCost) {
    }

    /** {@code shopBusinessId}: the linked VirtualBiz shop the goods are for (optional). */
    public record OrderRequest(@NotNull UUID supplierId, LocalDate expectedDate, UUID shopBusinessId,
            @Size(max = 4000) String notes, @NotEmpty @Size(max = 200) List<@Valid @NotNull LineInput> lines) {
    }

    public record CancelRequest(@NotBlank @Size(max = 500) String reason) {
    }

    public record ReceiveLine(@NotNull UUID lineId,
            @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 3) BigDecimal quantity) {
    }

    public record ReceiveRequest(LocalDate receivedOn, @NotEmpty @Size(max = 200) List<@Valid @NotNull ReceiveLine> lines,
            @Size(max = 500) String note) {
    }

    public record PaymentRequest(@NotNull LocalDate paidOn,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotNull @Pattern(regexp = "CASH|BANK_TRANSFER|KHQR|CARD|OTHER") String method, @Size(max = 120) String reference) {
    }

    // ── Responses ────────────────────────────────────────────────────────────────────

    public record PersonRef(UUID id, String name, String avatarUrl) {
    }

    /** {@code owed}: unpaid on its orders; {@code spentThisYear}: ordered this year. */
    public record SupplierView(UUID id, String name, String contactName, String email, String phone, String address,
            String taxId, int paymentTermsDays, String notes, boolean archived, BigDecimal owed, BigDecimal spentThisYear,
            int openOrders) {
    }

    public record LineView(UUID id, String description, String sku, BigDecimal quantity, BigDecimal unitCost,
            BigDecimal received, BigDecimal amount) {
    }

    public record ReceiptLine(UUID lineId, String description, String sku, BigDecimal quantity) {
    }

    public record ReceiptView(UUID id, LocalDate receivedOn, List<ReceiptLine> lines, String note, boolean stockRecorded,
            PersonRef by, Instant createdAt) {
    }

    public record PaymentView(UUID id, LocalDate paidOn, BigDecimal amount, String method, String reference, PersonRef by) {
    }

    /** {@code dueDate}: the order date plus the supplier's terms; {@code overdue}: due and not fully paid. */
    public record OrderSummary(UUID id, String number, UUID supplierId, String supplierName, String status,
            LocalDate orderDate, LocalDate expectedDate, LocalDate dueDate, boolean overdue, BigDecimal total,
            BigDecimal paid, BigDecimal balance, String shopName) {
    }

    public record OrderView(OrderSummary summary, UUID shopBusinessId, String notes, List<LineView> lines,
            List<ReceiptView> receipts, List<PaymentView> payments, String cancelReason, PersonRef createdBy,
            Instant createdAt, boolean canEdit, boolean canReceive, boolean canPay) {
    }

    public record SpendBySupplier(UUID supplierId, String supplierName, int orders, BigDecimal ordered, BigDecimal paid,
            BigDecimal owed) {
    }

    /** {@code owed}: unpaid on ordered goods; {@code overdue}: of that, past the supplier's terms. */
    public record PurchasingOverview(BigDecimal owed, BigDecimal overdue, int awaitingDelivery, int notInStock,
            BigDecimal orderedThisMonth, List<SpendBySupplier> spendThisYear) {
    }
}
