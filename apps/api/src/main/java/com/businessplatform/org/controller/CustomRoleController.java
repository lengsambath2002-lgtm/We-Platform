package com.businessplatform.org.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.org.payload.RolePayloads.*;
import com.businessplatform.org.service.CustomRoleService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Custom roles (Owners and Admins only): the permission catalogue, roles, and who holds them where. */
@Tag(name = "Custom roles")
@RestController
@RequestMapping("/api/orgs/{org}/roles")
@RequiredArgsConstructor
public class CustomRoleController {

    private final CustomRoleService roles;

    @GetMapping
    public ResponseEntity<ApiResponse<RolesView>> view(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(roles.view(org)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoleView>> create(@PathVariable String org, @Valid @RequestBody RoleRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(roles.save(org, null, in)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RoleView>> update(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody RoleRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(roles.save(org, id, in)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @PathVariable UUID id) {
        roles.delete(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/grants")
    public ResponseEntity<ApiResponse<GrantView>> grant(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody GrantRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(roles.grant(org, id, in)));
    }

    @DeleteMapping("/grants/{grantId}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable String org, @PathVariable UUID grantId) {
        roles.revoke(org, grantId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
