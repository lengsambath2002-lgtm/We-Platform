package com.businessplatform.oidc.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.oidc.payload.OidcPayloads.TokenResponse;
import com.businessplatform.oidc.service.ClientAuthenticator;
import com.businessplatform.oidc.service.OidcClients;
import com.businessplatform.oidc.service.OidcTokenService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public, client-authenticated: {@code application/x-www-form-urlencoded} code exchange (contract §3). Errors are
 * RFC 6749 JSON ({@link ClientApiErrors}).
 */
@Tag(name = "OpenID Connect")
@ClientApi
@RestController
@RequiredArgsConstructor
public class OidcTokenController {

    private final ClientAuthenticator authenticator;
    private final OidcTokenService tokens;

    @PostMapping("/api/oidc/token")
    public ResponseEntity<TokenResponse> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "code_verifier", required = false) String verifier) {
        OidcClients.Client client = authenticator.authenticate(authorization, clientId, clientSecret);
        TokenResponse out = tokens.exchange(client, grantType, code, redirectUri, verifier);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(out);
    }
}
