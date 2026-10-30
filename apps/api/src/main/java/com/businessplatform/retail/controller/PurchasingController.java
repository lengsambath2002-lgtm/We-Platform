package com.businessplatform.retail.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.retail.payload.PurchasingPayloads.*;
import com.businessplatform.retail.service.PurchasingService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Retail &amp; Commerce: suppliers and purchase orders (stock itself stays in VirtualBiz). */
@RestController
@RequestMapping("/api/orgs/{org}/retail")
@RequiredArgsConstructor
@Tag(name = "Retail purchasing")
public class PurchasingController {

    private final PurchasingService purchasing;

    @GetMapping("/purchasing")
    public ResponseEntity<ApiResponse<PurchasingOverview>> overview(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.overview(org)));
    }

    @GetMapping("/suppliers")
    public ResponseEntity<ApiResponse<List<SupplierView>>> suppliers(@PathVariable String org,
            @RequestParam(defaultValue = "false") boolean archived) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.suppliers(org, archived)));
    }

    @PostMapping("/suppliers")
    public ResponseEntity<ApiResponse<SupplierView>> createSupplier(@PathVariable String org, @Valid @RequestBody SupplierRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(purchasing.saveSupplier(org, null, in)));
    }

    @GetMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<SupplierView>> supplier(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.supplier(org, id)));
    }

    @PutMapping("/suppliers/{id}")
    public ResponseEntity<ApiResponse<SupplierView>> updateSupplier(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody SupplierRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.saveSupplier(org, id, in)));
    }

    public record ArchiveRequest(boolean archived) {
    }

    @PutMapping("/suppliers/{id}/archived")
    public ResponseEntity<ApiResponse<SupplierView>> archive(@PathVariable String org, @PathVariable UUID id,
            @RequestBody ArchiveRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.archiveSupplier(org, id, in.archived())));
    }

    @GetMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<List<OrderSummary>>> orders(@PathVariable String org,
            @RequestParam(required = false) String status, @RequestParam(required = false) UUID supplierId) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.orders(org, status, supplierId)));
    }

    @PostMapping("/purchase-orders")
    public ResponseEntity<ApiResponse<OrderView>> create(@PathVariable String org, @Valid @RequestBody OrderRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(purchasing.create(org, in)));
    }

    @GetMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<OrderView>> order(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.order(org, id)));
    }

    @PutMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<OrderView>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody OrderRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.update(org, id, in)));
    }

    @DeleteMapping("/purchase-orders/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        purchasing.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/purchase-orders/{id}/place")
    public ResponseEntity<ApiResponse<OrderView>> place(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.place(org, id)));
    }

    @PostMapping("/purchase-orders/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderView>> cancel(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody CancelRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.cancel(org, id, in)));
    }

    @PostMapping("/purchase-orders/{id}/receipts")
    public ResponseEntity<ApiResponse<OrderView>> receive(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody ReceiveRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.receive(org, id, in)));
    }

    public record StockRecordedRequest(boolean recorded) {
    }

    /** Ticks a delivery off as put into stock in VirtualBiz. */
    @PutMapping("/receipts/{id}/stock-recorded")
    public ResponseEntity<ApiResponse<OrderView>> stockRecorded(@PathVariable String org, @PathVariable UUID id,
            @RequestBody StockRecordedRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.stockRecorded(org, id, in.recorded())));
    }

    @PostMapping("/purchase-orders/{id}/payments")
    public ResponseEntity<ApiResponse<OrderView>> pay(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody PaymentRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(purchasing.pay(org, id, in)));
    }
}
