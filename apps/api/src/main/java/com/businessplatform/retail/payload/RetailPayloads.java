package com.businessplatform.retail.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The shop-link API (VirtualBiz → platform) and the Retail page. */
public final class RetailPayloads {

    private RetailPayloads() {
    }

    /** {@code PUT /api/integrations/virtualbiz/links/{businessId}} body. */
    public record LinkRequest(UUID orgId, String businessName, String linkedBy) {
    }

    /** The answer to a link (contract §4). */
    public record LinkResponse(UUID orgId, UUID businessId, String businessName, Instant linkedAt) {
    }

    public record Person(UUID id, String name) {
    }

    public record Shop(UUID businessId, String businessName, Instant linkedAt, Person linkedBy) {
    }

    /**
     * The organisation's shops. {@code configured}: VirtualBiz's address is set ({@code virtualbizUrl}); the page
     * builds Open shop ({@code /platform/open?business=…}) and Connect a shop ({@code /platform/connect?org=…}).
     */
    public record RetailShops(boolean configured, String virtualbizUrl, UUID orgId, boolean canManage,
            List<Shop> shops) {
    }
}
