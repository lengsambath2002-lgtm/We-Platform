package com.businessplatform.business.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.businessplatform.business.payload.BusinessPayloads.*;
import com.businessplatform.business.service.BizOverviewService;
import com.businessplatform.business.service.BizSettingsService;
import com.businessplatform.business.service.CustomerService;
import com.businessplatform.business.service.ExpenseCategoryService;
import com.businessplatform.business.service.ExpenseService;
import com.businessplatform.business.service.InvoiceService;
import com.businessplatform.business.service.ProductService;
import com.businessplatform.business.service.QuoteService;
import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.file.FileService;
import com.businessplatform.file.StoredFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * General Business: customers, products, quotes, invoices with payments, expenses and settings. Who may do
 * what is described on {@code BusinessAccess}; lists are paged ({@code page} from 0, {@code size} up to 100).
 */
@RestController
@RequestMapping("/api/orgs/{org}/business")
@RequiredArgsConstructor
@Tag(name = "General Business")
public class BusinessController {

    private final BizOverviewService overview;
    private final BizSettingsService settings;
    private final CustomerService customers;
    private final ProductService products;
    private final QuoteService quotes;
    private final InvoiceService invoices;
    private final ExpenseCategoryService categories;
    private final ExpenseService expenses;
    private final FileService files;

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Overview>> overview(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(overview.overview(org)));
    }

    // ── Settings ─────────────────────────────────────────────────────────────────────

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> settings(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(settings.get(org)));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsView>> updateSettings(@PathVariable String org,
            @Valid @RequestBody SettingsRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(settings.update(org, in)));
    }

    // ── Customers ────────────────────────────────────────────────────────────────────

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<PageResponse<CustomerView>>> customers(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(customers.list(org, q, archived, page, size)));
    }

    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<CustomerView>> createCustomer(@PathVariable String org,
            @Valid @RequestBody CustomerRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(customers.create(org, in)));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerDetail>> customer(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customers.get(org, id)));
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<ApiResponse<CustomerView>> updateCustomer(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(customers.update(org, id, in)));
    }

    @PostMapping("/customers/{id}/archive")
    public ResponseEntity<ApiResponse<CustomerView>> archive(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customers.archive(org, id, true)));
    }

    @PostMapping("/customers/{id}/restore")
    public ResponseEntity<ApiResponse<CustomerView>> restore(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(customers.archive(org, id, false)));
    }

    // ── Products ─────────────────────────────────────────────────────────────────────

    /** {@code all}: switched-off ones too. */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ProductView>>> products(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean all) {
        return ResponseEntity.ok(ApiResponse.ok(products.list(org, q, all)));
    }

    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductView>> createProduct(@PathVariable String org,
            @Valid @RequestBody ProductRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(products.save(org, null, in)));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResponse<ProductView>> updateProduct(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ProductRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(products.save(org, id, in)));
    }

    // ── Quotes ───────────────────────────────────────────────────────────────────────

    @GetMapping("/quotes")
    public ResponseEntity<ApiResponse<PageResponse<QuoteSummary>>> quotes(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.list(org, q, status, customerId, page, size)));
    }

    @PostMapping("/quotes")
    public ResponseEntity<ApiResponse<QuoteView>> createQuote(@PathVariable String org, @Valid @RequestBody QuoteRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(quotes.create(org, in)));
    }

    @GetMapping("/quotes/{id}")
    public ResponseEntity<ApiResponse<QuoteView>> quote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.get(org, id)));
    }

    @PutMapping("/quotes/{id}")
    public ResponseEntity<ApiResponse<QuoteView>> updateQuote(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody QuoteRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.update(org, id, in)));
    }

    @DeleteMapping("/quotes/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQuote(@PathVariable String org, @PathVariable UUID id) {
        quotes.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/quotes/{id}/send")
    public ResponseEntity<ApiResponse<QuoteView>> sendQuote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.mark(org, id, "SENT")));
    }

    @PostMapping("/quotes/{id}/accept")
    public ResponseEntity<ApiResponse<QuoteView>> acceptQuote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.mark(org, id, "ACCEPTED")));
    }

    @PostMapping("/quotes/{id}/decline")
    public ResponseEntity<ApiResponse<QuoteView>> declineQuote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(quotes.mark(org, id, "DECLINED")));
    }

    /** Makes a draft invoice from the quote; answers the invoice. */
    @PostMapping("/quotes/{id}/convert")
    public ResponseEntity<ApiResponse<InvoiceView>> convertQuote(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.status(201).body(ApiResponse.ok(quotes.convert(org, id)));
    }

    // ── Invoices ─────────────────────────────────────────────────────────────────────

    /** {@code status}: DRAFT, ISSUED, PARTIALLY_PAID, PAID, VOID or OVERDUE. */
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<PageResponse<InvoiceSummary>>> invoices(@PathVariable String org,
            @RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID customerId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.list(org, q, status, customerId, page, size)));
    }

    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<InvoiceView>> createInvoice(@PathVariable String org,
            @Valid @RequestBody InvoiceRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(invoices.create(org, in)));
    }

    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> invoice(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.get(org, id)));
    }

    @PutMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> updateInvoice(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody InvoiceRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.update(org, id, in)));
    }

    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable String org, @PathVariable UUID id) {
        invoices.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/invoices/{id}/issue")
    public ResponseEntity<ApiResponse<InvoiceView>> issue(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.issue(org, id)));
    }

    @PostMapping("/invoices/{id}/payments")
    public ResponseEntity<ApiResponse<InvoiceView>> pay(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(invoices.pay(org, id, in)));
    }

    @DeleteMapping("/invoices/{id}/payments/{paymentId}")
    public ResponseEntity<ApiResponse<InvoiceView>> removePayment(@PathVariable String org, @PathVariable UUID id,
            @PathVariable UUID paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.removePayment(org, id, paymentId)));
    }

    @PostMapping("/invoices/{id}/void")
    public ResponseEntity<ApiResponse<InvoiceView>> voidInvoice(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody VoidRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.voidInvoice(org, id, in)));
    }

    @GetMapping("/invoices/{id}/activity")
    public ResponseEntity<ApiResponse<List<ActivityView>>> invoiceHistory(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.history(org, id)));
    }

    // ── Expenses ─────────────────────────────────────────────────────────────────────

    @GetMapping("/expense-categories")
    public ResponseEntity<ApiResponse<List<CategoryView>>> categories(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(categories.list(org)));
    }

    @PostMapping("/expense-categories")
    public ResponseEntity<ApiResponse<CategoryView>> createCategory(@PathVariable String org,
            @Valid @RequestBody CategoryRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(categories.save(org, null, in)));
    }

    @PutMapping("/expense-categories/{id}")
    public ResponseEntity<ApiResponse<CategoryView>> updateCategory(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(categories.save(org, id, in)));
    }

    /** Members get their own; Admins everyone's ({@code mine} for their own). */
    @GetMapping("/expenses")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseView>>> expenses(@PathVariable String org,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.list(org, status, mine, page, size)));
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiResponse<ExpenseView>> createExpense(@PathVariable String org,
            @Valid @RequestBody ExpenseRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(expenses.create(org, in)));
    }

    @GetMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<ExpenseView>> expense(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.get(org, id)));
    }

    @PutMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<ExpenseView>> updateExpense(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.update(org, id, in)));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExpense(@PathVariable String org, @PathVariable UUID id) {
        expenses.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/expenses/{id}/submit")
    public ResponseEntity<ApiResponse<ExpenseView>> submitExpense(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.submit(org, id)));
    }

    @PostMapping("/expenses/{id}/paid")
    public ResponseEntity<ApiResponse<ExpenseView>> expensePaid(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.markPaid(org, id)));
    }

    @PostMapping(value = "/expenses/{id}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ExpenseView>> attachReceipt(@PathVariable String org, @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.attachReceipt(org, id, file)));
    }

    @DeleteMapping("/expenses/{id}/receipt")
    public ResponseEntity<ApiResponse<ExpenseView>> removeReceipt(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(expenses.removeReceipt(org, id)));
    }

    /** The receipt itself; only for the expense's person and Admins. */
    @GetMapping("/expenses/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable String org, @PathVariable UUID id) {
        StoredFile f = expenses.receipt(org, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.getName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(files.read(f));
    }
}
