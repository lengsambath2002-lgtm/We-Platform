package com.businessplatform.identity.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;

/**
 * Verifies Firebase ID tokens as Firebase documents it, without the Admin SDK or a service account: RS256 signed with
 * Google's public keys (cached), issuer {@code https://securetoken.google.com/<project>}, audience the project,
 * not expired, a subject. {@code platform.firebase.project-id} empty: Google sign-in is off.
 */
@Component
class GoogleFirebaseTokens implements FirebaseTokens {

    static final String KEYS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final String project;
    private final NimbusJwtDecoder decoder;

    GoogleFirebaseTokens(@Value("${platform.firebase.project-id:}") String project) {
        this.project = project == null ? "" : project.strip();
        if (this.project.isEmpty()) {
            this.decoder = null;
            return;
        }
        this.decoder = NimbusJwtDecoder.withJwkSetUri(KEYS).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://securetoken.google.com/" + this.project),
                jwt -> jwt.getAudience() != null && jwt.getAudience().contains(this.project)
                        && jwt.getSubject() != null && !jwt.getSubject().isBlank()
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "wrong audience", null))));
    }

    @Override
    public boolean configured() {
        return decoder != null;
    }

    @Override
    public Identity verify(String idToken) {
        if (decoder == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        Object firebase = jwt.getClaims().get("firebase");
        String provider = firebase instanceof Map<?, ?> m && m.get("sign_in_provider") != null
                ? m.get("sign_in_provider").toString() : null;
        return new Identity(jwt.getSubject(), jwt.getClaimAsString("email"), Boolean.TRUE.equals(jwt.getClaim("email_verified")),
                jwt.getClaimAsString("name"), jwt.getClaimAsString("picture"), provider);
    }

}
