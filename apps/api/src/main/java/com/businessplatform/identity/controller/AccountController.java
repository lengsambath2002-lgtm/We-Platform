package com.businessplatform.identity.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.security.CurrentUser;
import com.businessplatform.identity.payload.AccountPayloads.ChangePasswordRequest;
import com.businessplatform.identity.payload.AccountPayloads.SessionInfo;
import com.businessplatform.identity.service.AccountService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** The signed-in person's password and devices. */
@Tag(name = "Me")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService account;

    /** Changes the password and signs out every other device. */
    @PostMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest in) {
        account.changePassword(CurrentUser.id(), CurrentUser.sessionId(), in.currentPassword(), in.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfo>>> sessions() {
        return ResponseEntity.ok(ApiResponse.ok(account.sessions(CurrentUser.id(), CurrentUser.sessionId())));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<ApiResponse<Void>> revoke(@PathVariable UUID id) {
        account.revokeSession(CurrentUser.id(), id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record SignedOut(int count) {
    }

    @PostMapping("/sessions/sign-out-others")
    public ResponseEntity<ApiResponse<SignedOut>> revokeOthers() {
        return ResponseEntity.ok(ApiResponse.ok(
                new SignedOut(account.revokeOtherSessions(CurrentUser.id(), CurrentUser.sessionId()))));
    }
}
