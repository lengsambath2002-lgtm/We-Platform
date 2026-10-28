package com.businessplatform.oidc.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeCheck;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeDecision;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeParams;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeResult;
import com.businessplatform.oidc.service.OidcAuthorizeService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The web app's {@code /oauth/authorize} page calls these (contract §2). */
@Tag(name = "OpenID Connect")
@RestController
@RequiredArgsConstructor
public class OidcAuthorizeController {

    private final OidcAuthorizeService authorize;

    /**
     * Public (the page asks before the visitor signs in): is the request valid, and what next. With an access
     * token it also says whether the person already allowed this client.
     */
    @GetMapping("/api/oidc/authorize/check")
    public ResponseEntity<ApiResponse<AuthorizeCheck>> check(@RequestParam Map<String, String> query) {
        return ResponseEntity.ok(ApiResponse.ok(authorize.check(AuthorizeParams.of(query), CurrentUser.optionalId())));
    }

    /** Signed in: allow (remembered) or cancel; answers the URL to send the browser to. */
    @PostMapping("/api/oidc/authorize")
    public ResponseEntity<ApiResponse<AuthorizeResult>> decide(@Valid @RequestBody AuthorizeDecision in) {
        return ResponseEntity.ok(ApiResponse.ok(new AuthorizeResult(
                authorize.decide(in.request(), in.approve(), CurrentUser.id(), CurrentUser.sessionId()))));
    }
}
