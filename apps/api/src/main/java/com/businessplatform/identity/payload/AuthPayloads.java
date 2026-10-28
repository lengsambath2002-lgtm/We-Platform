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

    public record SignInRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserResponse(UUID id, String name, String email, String locale, String avatarUrl) {
    }

    /** The access token (15 minutes) for {@code Authorization: Bearer}; the refresh token is a cookie (web) or here (mobile). */
    public record SessionResponse(String accessToken, long expiresIn, UserResponse user, String refreshToken) {
    }

    public record UpdateMeRequest(@Size(min = 2, max = 80) String name,
            @Pattern(regexp = "en|km", message = "en or km") String locale) {
    }
}
