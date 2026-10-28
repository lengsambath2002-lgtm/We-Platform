package com.businessplatform.retail.controller;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.oidc.controller.ClientApi;
import com.businessplatform.oidc.service.ClientApiException;
import com.businessplatform.oidc.service.ClientAuthenticator;
import com.businessplatform.retail.payload.RetailPayloads.LinkRequest;
import com.businessplatform.retail.payload.RetailPayloads.LinkResponse;
import com.businessplatform.retail.service.ShopLinkService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * VirtualBiz reports shop links (contract §4), authenticated with HTTP Basic client credentials. Errors are
 * {@code {"error": "...", "error_description": "..."}}: 400 invalid_request, 401 invalid_client, 403 forbidden,
 * 404 not_found.
 */
@Tag(name = "Integrations: VirtualBiz")
@ClientApi
@RestController
@RequestMapping("/api/integrations/virtualbiz/links/{businessId}")
@RequiredArgsConstructor
public class ShopLinkApiController {

    private final ClientAuthenticator authenticator;
    private final ShopLinkService links;

    /** Whether the shop is still linked (VirtualBiz checks before granting staff access through the link). */
    @GetMapping
    public ResponseEntity<LinkResponse> get(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String businessId) {
        authenticator.basic(auth);
        return ResponseEntity.ok(links.get(id(businessId)));
    }

    @PutMapping
    public ResponseEntity<LinkResponse> link(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String businessId, @RequestBody(required = false) LinkRequest in) {
        String client = authenticator.basic(auth).id();
        return ResponseEntity.ok(links.link(client, id(businessId), in));
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
            @PathVariable String businessId) {
        String client = authenticator.basic(auth).id();
        links.unlink(client, id(businessId));
        return ResponseEntity.noContent().build();
    }

    private static UUID id(String businessId) {
        try {
            return UUID.fromString(businessId);
        } catch (IllegalArgumentException e) {
            throw ClientApiException.invalidRequest("businessId must be a UUID");
        }
    }
}
