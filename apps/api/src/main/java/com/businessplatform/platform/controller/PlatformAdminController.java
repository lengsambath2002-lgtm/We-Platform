package com.businessplatform.platform.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.api.PageResponse;
import com.businessplatform.identity.controller.AuthController;
import com.businessplatform.platform.payload.PlatformPayloads.AuditRow;
import com.businessplatform.platform.payload.PlatformPayloads.OrgDetail;
import com.businessplatform.platform.payload.PlatformPayloads.OrgRow;
import com.businessplatform.platform.payload.PlatformPayloads.ReasonRequest;
import com.businessplatform.platform.payload.PlatformPayloads.Stats;
import com.businessplatform.platform.payload.PlatformPayloads.Toggle;
import com.businessplatform.platform.payload.PlatformPayloads.UserDetail;
import com.businessplatform.platform.payload.PlatformPayloads.UserRow;
import com.businessplatform.platform.service.PlatformAdminService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The platform console (super admins only): every organisation and account, and the platform audit log. */
@Tag(name = "Platform admin")
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformAdminService admin;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Stats>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(admin.stats()));
    }

    /** {@code status}: ACTIVE, SUSPENDED or DELETED (all when absent). */
    @GetMapping("/orgs")
    public ResponseEntity<ApiResponse<PageResponse<OrgRow>>> organisations(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(admin.organisations(q, status, page, size)));
    }

    @GetMapping("/orgs/{id}")
    public ResponseEntity<ApiResponse<OrgDetail>> organisation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.organisation(id)));
    }

    @PostMapping("/orgs/{id}/suspend")
    public ResponseEntity<ApiResponse<OrgDetail>> suspend(@PathVariable UUID id, @Valid @RequestBody ReasonRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(admin.suspend(id, in.reason())));
    }

    @PostMapping("/orgs/{id}/reactivate")
    public ResponseEntity<ApiResponse<OrgDetail>> reactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.reactivate(id)));
    }

    @PostMapping("/orgs/{id}/restore")
    public ResponseEntity<ApiResponse<OrgDetail>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.restore(id)));
    }

    @PutMapping("/orgs/{id}/modules/{key}")
    public ResponseEntity<ApiResponse<OrgDetail>> setModule(@PathVariable UUID id, @PathVariable String key,
            @RequestBody Toggle in) {
        return ResponseEntity.ok(ApiResponse.ok(admin.setModule(id, key, in.enabled())));
    }

    /** {@code status}: ACTIVE, DISABLED or SUPER_ADMIN (all when absent). */
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<UserRow>>> users(@RequestParam(required = false) String q,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(ApiResponse.ok(admin.users(q, status, page, size)));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<ApiResponse<UserDetail>> user(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.user(id)));
    }

    @PostMapping("/users/{id}/disable")
    public ResponseEntity<ApiResponse<UserDetail>> disable(@PathVariable UUID id, @Valid @RequestBody ReasonRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(admin.disable(id, in.reason())));
    }

    @PostMapping("/users/{id}/enable")
    public ResponseEntity<ApiResponse<UserDetail>> enable(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.enable(id)));
    }

    @PostMapping("/users/{id}/sign-out")
    public ResponseEntity<ApiResponse<UserDetail>> signOut(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(admin.signOut(id)));
    }

    @PostMapping("/users/{id}/password-reset")
    public ResponseEntity<ApiResponse<UserDetail>> passwordReset(@PathVariable UUID id, HttpServletRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(admin.sendPasswordReset(id, AuthController.ip(req))));
    }

    @PutMapping("/users/{id}/super-admin")
    public ResponseEntity<ApiResponse<UserDetail>> superAdmin(@PathVariable UUID id, @RequestBody Toggle in) {
        return ResponseEntity.ok(ApiResponse.ok(admin.setSuperAdmin(id, in.enabled())));
    }

    /** {@code target}: only entries about that organisation or account. */
    @GetMapping("/audit")
    public ResponseEntity<ApiResponse<PageResponse<AuditRow>>> audit(@RequestParam(required = false) UUID target,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.ok(admin.audit(target, page, size)));
    }
}
