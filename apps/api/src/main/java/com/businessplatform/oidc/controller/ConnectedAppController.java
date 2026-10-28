package com.businessplatform.oidc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.oidc.payload.OidcPayloads.ConnectedApp;
import com.businessplatform.oidc.service.ConnectedAppService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** The signed-in person's connected apps (single sign-on approvals). */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/me/connected-apps")
@RequiredArgsConstructor
public class ConnectedAppController {

    private final ConnectedAppService apps;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConnectedApp>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(apps.list(CurrentUser.id())));
    }

    @DeleteMapping("/{clientId}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable String clientId) {
        apps.revoke(CurrentUser.id(), clientId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
