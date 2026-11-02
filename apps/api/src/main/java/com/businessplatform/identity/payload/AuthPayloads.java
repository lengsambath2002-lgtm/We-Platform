package com.businessplatform.identity.payload;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sign-up, sign-in and the session the client keeps. */
public final class AuthPayloads {

    private AuthPayloads() {
    }

    public record SignUpRequest(@NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

    public record FirebaseRequest(@NotBlank @Size(max = 4096) String idToken) {
    }

    public record SignInRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /** {@code superAdmin}: may open the platform console (/admin); {@code emailVerified}: the emailed link was opened. */
    public record UserResponse(UUID id, String name, String email, String locale, String avatarUrl, boolean superAdmin,
            boolean emailVerified) {
    }

    /**
     * The access token (15 minutes) for {@code Authorization: Bearer}; the refresh token is a cookie (web) or here
     * (mobile). With two-step sign-in on, a right password answers only {@code twoStep}: the challenge to send back
     * with a code to {@code POST /api/auth/two-step}.
     */
    public record SessionResponse(String accessToken, long expiresIn, UserResponse user, String refreshToken, String twoStep) {

        public SessionResponse(String accessToken, long expiresIn, UserResponse user, String refreshToken) {
            this(accessToken, expiresIn, user, refreshToken, null);
        }

        public static SessionResponse challenge(String token) {
            return new SessionResponse(null, 0, null, null, token);
        }
    }

    public record TwoStepRequest(@NotBlank @Size(max = 100) String challenge, @NotBlank @Size(max = 20) String code) {
    }

    public record UpdateMeRequest(@Size(min = 2, max = 80) String name,
            @Pattern(regexp = "en|km", message = "en or km") String locale) {
    }
}
