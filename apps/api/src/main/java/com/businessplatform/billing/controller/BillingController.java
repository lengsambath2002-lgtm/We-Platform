package com.businessplatform.billing.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.billing.payload.BillingPayloads.*;
import com.businessplatform.billing.service.SubscriptionService;
import com.businessplatform.common.api.ApiResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Plans (public, for pricing) and an organisation's subscription. */
@Tag(name = "Plans & billing")
@RestController
@RequiredArgsConstructor
public class BillingController {

    private final SubscriptionService subscriptions;
    private final com.businessplatform.billing.service.PlatformInvoiceService invoices;

    @GetMapping("/api/plans")
    public ResponseEntity<ApiResponse<List<PlanView>>> plans() {
        return ResponseEntity.ok(ApiResponse.ok(subscriptions.publicPlans()));
    }

    @GetMapping("/api/orgs/{org}/subscription")
    public ResponseEntity<ApiResponse<SubscriptionView>> get(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptions.view(org)));
    }

    @PutMapping("/api/orgs/{org}/subscription")
    public ResponseEntity<ApiResponse<SubscriptionView>> change(@PathVariable String org, @Valid @RequestBody ChangeRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptions.change(org, in)));
    }

    @GetMapping("/api/orgs/{org}/invoices")
    public ResponseEntity<ApiResponse<List<InvoiceView>>> invoices(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.list(org)));
    }

    @GetMapping("/api/orgs/{org}/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceView>> invoice(@PathVariable String org, @PathVariable java.util.UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.get(org, id)));
    }

    /** "We paid" with the bank's transfer reference; the platform confirms. */
    @PostMapping("/api/orgs/{org}/invoices/{id}/claim")
    public ResponseEntity<ApiResponse<InvoiceView>> claim(@PathVariable String org, @PathVariable java.util.UUID id,
            @Valid @RequestBody ClaimRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(invoices.claim(org, id, in)));
    }

    @PostMapping("/api/orgs/{org}/subscription/cancel")
    public ResponseEntity<ApiResponse<SubscriptionView>> cancel(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(subscriptions.cancel(org)));
    }
}
