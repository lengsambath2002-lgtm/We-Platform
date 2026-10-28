package com.businessplatform.identity.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.businessplatform.identity.repository.UserSessionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Refuses an access token whose device was signed out (sign-out, "sign out other devices", a password
 * change or reset, refresh-token reuse), so signing out takes effect at once instead of when the
 * 15-minute token expires. Picked up by the JWT decoder in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class SignedInValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error SIGNED_OUT = new OAuth2Error("invalid_token", "Signed out", null);

    private final UserSessionRepository sessions;
    private final Clock clock;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sid = jwt.getClaimAsString("sid");
        UUID id;
        try {
            id = UUID.fromString(sid);
        } catch (RuntimeException e) {
            return OAuth2TokenValidatorResult.failure(SIGNED_OUT);
        }
        return sessions.isSignedIn(id, clock.instant()) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(SIGNED_OUT);
    }
}
