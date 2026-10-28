package com.businessplatform.org.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.module.service.ModuleService;
import com.businessplatform.org.payload.OrgPayloads.*;
import com.businessplatform.org.service.OrgService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Organisations, their context for the shell, modules, members, invitations and teams. */
@Tag(name = "Organisations")
@RestController
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgs;
    private final ModuleService modules;

    @GetMapping("/api/orgs")
    public ResponseEntity<ApiResponse<List<OrgSummary>>> mine() {
        return ResponseEntity.ok(ApiResponse.ok(orgs.mine()));
    }

    @PostMapping("/api/orgs")
    public ResponseEntity<ApiResponse<OrgSummary>> create(@Valid @RequestBody CreateOrgRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(orgs.create(in)));
    }

    @GetMapping("/api/orgs/{org}/context")
    public ResponseEntity<ApiResponse<OrgContextResponse>> context(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.context(org)));
    }

    @PatchMapping("/api/orgs/{org}")
    public ResponseEntity<ApiResponse<OrgSummary>> update(@PathVariable String org,
            @Valid @RequestBody UpdateOrgRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.update(org, in)));
    }

    /** Owners only; body {@code {"name": "<the organisation's name>"}} confirms. */
    @DeleteMapping("/api/orgs/{org}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String org, @Valid @RequestBody DeleteOrgRequest in) {
        orgs.delete(org, in);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/api/orgs/{org}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable String org) {
        orgs.leave(org);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/api/modules")
    public ResponseEntity<ApiResponse<List<ModuleService.ModuleInfo>>> registry() {
        return ResponseEntity.ok(ApiResponse.ok(modules.catalogue()));
    }

    @PutMapping("/api/orgs/{org}/modules/{key}")
    public ResponseEntity<ApiResponse<OrgContextResponse>> setModule(@PathVariable String org,
            @PathVariable String key, @RequestBody ModuleToggle in) {
        orgs.setModule(org, key, in.enabled());
        return ResponseEntity.ok(ApiResponse.ok(orgs.context(org)));
    }

    public record ModuleToggle(boolean enabled) {
    }

    @GetMapping("/api/orgs/{org}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> members(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.members(org)));
    }

    @PatchMapping("/api/orgs/{org}/members/{id}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody UpdateMemberRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.updateMember(org, id, in)));
    }

    @GetMapping("/api/orgs/{org}/invitations")
    public ResponseEntity<ApiResponse<List<InvitationResponse>>> invitations(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.pendingInvitations(org)));
    }

    @PostMapping("/api/orgs/{org}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> invite(@PathVariable String org,
            @Valid @RequestBody InviteRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(orgs.invite(org, in)));
    }

    @PostMapping("/api/orgs/{org}/invitations/{id}/resend")
    public ResponseEntity<ApiResponse<InvitationResponse>> resend(@PathVariable String org, @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.resendInvitation(org, id)));
    }

    @DeleteMapping("/api/orgs/{org}/invitations/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable String org, @PathVariable UUID id) {
        orgs.revokeInvitation(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Public: who invited this email to which organisation (the invite page before signing in). */
    @GetMapping("/api/invitations/{token}")
    public ResponseEntity<ApiResponse<InvitationPreview>> preview(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.preview(token)));
    }

    @PostMapping("/api/invitations/{token}/accept")
    public ResponseEntity<ApiResponse<OrgSummary>> accept(@PathVariable String token) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.accept(token)));
    }

    @GetMapping("/api/orgs/{org}/teams")
    public ResponseEntity<ApiResponse<List<TeamResponse>>> teams(@PathVariable String org) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.teams(org)));
    }

    @PostMapping("/api/orgs/{org}/teams")
    public ResponseEntity<ApiResponse<TeamResponse>> createTeam(@PathVariable String org,
            @Valid @RequestBody TeamRequest in) {
        return ResponseEntity.status(201).body(ApiResponse.ok(orgs.saveTeam(org, null, in)));
    }

    @PutMapping("/api/orgs/{org}/teams/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> updateTeam(@PathVariable String org, @PathVariable UUID id,
            @Valid @RequestBody TeamRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(orgs.saveTeam(org, id, in)));
    }

    @DeleteMapping("/api/orgs/{org}/teams/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable String org, @PathVariable UUID id) {
        orgs.deleteTeam(org, id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
