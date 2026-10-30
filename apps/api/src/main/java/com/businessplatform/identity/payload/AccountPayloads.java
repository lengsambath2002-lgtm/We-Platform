package com.businessplatform.identity.payload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Password change and reset, and the signed-in devices. */
public final class AccountPayloads {

    private AccountPayloads() {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetTokenRequest(@NotBlank @Size(max = 100) String token) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(max = 100) String token,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

    /** Whether a reset link can still be used (the page checks before asking for a password). */
    /** {@code recoveryCodesLeft}: unused recovery codes. */
    public record TwoStepStatus(boolean enabled, Instant enabledAt, int recoveryCodesLeft) {
    }

    /** The secret to type in, and the otpauth:// address to show as a QR code. */
    public record TwoStepSetup(String secret, String uri) {
    }

    public record TwoStepCodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    public record TwoStepOffRequest(@NotBlank String password, @NotBlank @Size(max = 20) String code) {
    }

    public record PasswordRequest(@NotBlank String password) {
    }

    public record RecoveryCodes(List<String> codes) {
    }

    public record ResetTokenState(boolean valid) {
    }

    /** One signed-in device; {@code id} stays the same across refreshes. */
    public record SessionInfo(UUID id, String userAgent, String ip, Instant signedInAt, Instant lastUsedAt,
            boolean current) {
    }
}
