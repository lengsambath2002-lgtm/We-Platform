package com.businessplatform.oidc.controller;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.oidc.service.OidcKeys;
import com.businessplatform.oidc.service.OidcTokenService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Public: the provider's metadata and its public signing keys (contract §1). */
@Tag(name = "OpenID Connect")
@RestController
@RequiredArgsConstructor
public class OidcDiscoveryController {

    private final OidcTokenService tokens;
    private final OidcKeys keys;

    @GetMapping("/api/oidc/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> discovery() {
        String iss = tokens.issuer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("issuer", iss);
        m.put("authorization_endpoint", iss + "/oauth/authorize");
        m.put("token_endpoint", iss + "/api/oidc/token");
        m.put("jwks_uri", iss + "/api/oidc/jwks");
        m.put("response_types_supported", List.of("code"));
        m.put("grant_types_supported", List.of("authorization_code"));
        m.put("code_challenge_methods_supported", List.of("S256"));
        m.put("id_token_signing_alg_values_supported", List.of("RS256"));
        m.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        m.put("scopes_supported", List.of("openid", "email", "profile", "orgs"));
        m.put("subject_types_supported", List.of("public"));
        m.put("claims_supported", List.of("iss", "aud", "sub", "iat", "exp", "auth_time", "nonce", "email",
                "email_verified", "name", "locale", "orgs"));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).body(m);
    }

    @GetMapping("/api/oidc/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(keys.publicJwks());
    }
}
