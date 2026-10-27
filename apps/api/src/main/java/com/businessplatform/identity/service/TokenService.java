package com.businessplatform.identity.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.businessplatform.common.security.AuthProperties;

import lombok.RequiredArgsConstructor;

/** Issues short-lived access tokens (subject = user id, sid = session id). */
@Service
@RequiredArgsConstructor
public class TokenService {

    private final JwtEncoder encoder;
    private final AuthProperties props;
    private final Clock clock;

    public String accessToken(UUID userId, UUID sessionId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("business-platform")
                .subject(userId.toString())
                .claim("sid", sessionId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTtl()))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long accessSeconds() {
        return props.accessTtl().toSeconds();
    }
}
