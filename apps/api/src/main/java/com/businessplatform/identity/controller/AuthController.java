package com.businessplatform.identity.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.security.AuthProperties;
import com.businessplatform.identity.payload.AccountPayloads.ForgotPasswordRequest;
import com.businessplatform.identity.payload.AccountPayloads.ResetPasswordRequest;
import com.businessplatform.identity.payload.AccountPayloads.ResetTokenRequest;
import com.businessplatform.identity.payload.AccountPayloads.ResetTokenState;
import com.businessplatform.identity.payload.AuthPayloads.SessionResponse;
import com.businessplatform.identity.payload.AuthPayloads.SignInRequest;
import com.businessplatform.identity.payload.AuthPayloads.SignUpRequest;
import com.businessplatform.identity.service.AccountService;
import com.businessplatform.identity.service.AuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sign-up / sign-in / refresh / sign-out. Web clients keep the refresh token in an HttpOnly cookie
 * (path /api/auth); mobile clients send {@code X-Client: mobile} and get it in the body instead.
 */
@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    static final String COOKIE = "bp_refresh";

    private final AuthService auth;
    private final AccountService account;
    private final AuthProperties props;

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<SessionResponse>> signUp(@Valid @RequestBody SignUpRequest in,
            HttpServletRequest http, @RequestHeader(value = "X-Client", required = false) String client) {
        return session(auth.signUp(in.name(), in.email(), in.password(), ua(http), ip(http)), client);
    }

    @PostMapping("/sign-in")
    public ResponseEntity<ApiResponse<SessionResponse>> signIn(@Valid @RequestBody SignInRequest in,
            HttpServletRequest http, @RequestHeader(value = "X-Client", required = false) String client) {
        return session(auth.signIn(in.email(), in.password(), ua(http), ip(http)), client);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<SessionResponse>> refresh(
            @CookieValue(value = COOKIE, required = false) String cookie,
            @RequestBody(required = false) RefreshBody body, HttpServletRequest http,
            @RequestHeader(value = "X-Client", required = false) String client) {
        String token = cookie != null ? cookie : body == null ? null : body.refreshToken();
        return session(auth.refresh(token, ua(http), ip(http)), client);
    }

    @PostMapping("/sign-out")
    public ResponseEntity<ApiResponse<Void>> signOut(@CookieValue(value = COOKIE, required = false) String cookie,
            @RequestBody(required = false) RefreshBody body) {
        auth.signOut(cookie != null ? cookie : body == null ? null : body.refreshToken());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
                .body(ApiResponse.ok(null));
    }

    /** Always answers the same, whether or not an account uses the email (TOO_MANY_REQUESTS aside). */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest in,
            HttpServletRequest http) {
        account.forgotPassword(in.email(), ip(http));
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** Whether a reset link still works (the token travels in the body, not the URL, to stay out of logs). */
    @PostMapping("/reset-password/check")
    public ResponseEntity<ApiResponse<ResetTokenState>> checkReset(@Valid @RequestBody ResetTokenRequest in) {
        return ResponseEntity.ok(ApiResponse.ok(new ResetTokenState(account.resetTokenValid(in.token()))));
    }

    /** Sets the new password, signs out every device and signs in on this one. */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<SessionResponse>> resetPassword(@Valid @RequestBody ResetPasswordRequest in,
            HttpServletRequest http, @RequestHeader(value = "X-Client", required = false) String client) {
        return session(account.resetPassword(in.token(), in.password(), ua(http), ip(http)), client);
    }

    public record RefreshBody(String refreshToken) {
    }

    private ResponseEntity<ApiResponse<SessionResponse>> session(SessionResponse s, String client) {
        if ("mobile".equalsIgnoreCase(client)) {
            return ResponseEntity.ok(ApiResponse.ok(s));
        }
        SessionResponse web = new SessionResponse(s.accessToken(), s.expiresIn(), s.user(), null);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), props.refreshTtl().toSeconds()).toString())
                .body(ApiResponse.ok(web));
    }

    private ResponseCookie cookie(String value, long maxAge) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(props.cookieSecure())
                .sameSite("Lax").path("/api/auth").maxAge(maxAge).build();
    }

    static String ua(HttpServletRequest http) {
        return http.getHeader(HttpHeaders.USER_AGENT);
    }

    static String ip(HttpServletRequest http) {
        String f = http.getHeader("X-Forwarded-For");
        return f != null && !f.isBlank() ? f.split(",")[0].trim() : http.getRemoteAddr();
    }
}
