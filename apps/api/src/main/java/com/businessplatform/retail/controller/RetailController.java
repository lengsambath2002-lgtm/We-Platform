package com.businessplatform.retail.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.retail.payload.RetailPayloads.RetailShops;
import com.businessplatform.retail.service.ShopLinkService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Retail &amp; Commerce: the organisation's linked VirtualBiz shops (permission {@code retail.view} / {@code retail.manage}). */
@Tag(name = "Retail & Commerce")
@RestController
@RequestMapping("/api/orgs/{org}/retail/shops")
@RequiredArgsConstructor
public class RetailController {

    private final ShopLinkService links;

    @GetMapping
    public ResponseEntity<ApiResponse<RetailShops>> shops(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(links.list(org)));
    }

    /** Owners and Admins. */
    @DeleteMapping("/{businessId}")
    public ResponseEntity<ApiResponse<Void>> unlink(@PathVariable String org, @PathVariable UUID businessId) {
        links.unlinkByAdmin(org, businessId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
