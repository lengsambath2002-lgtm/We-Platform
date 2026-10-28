package com.businessplatform.oidc.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.businessplatform.identity.service.AttemptLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Authenticates a client server: HTTP Basic ({@code client_id:client_secret}, each form-urlencoded as RFC 6749
 * §2.3.1 says) or {@code client_id} + {@code client_secret} in the body, never both. Each claimed client id may
 * try {@value #PER_MINUTE} requests a minute per API instance.
 */
@Slf4j
@Component
public class ClientAuthenticator {

    static final int PER_MINUTE = 60;

    private final OidcClients clients;
    private final AttemptLimiter limiter;

    public ClientAuthenticator(OidcClients clients, Clock clock) {
        this.clients = clients;
        this.limiter = new AttemptLimiter(PER_MINUTE, Duration.ofMinutes(1), clock);
    }

    /** Basic only (the shop-link API). */
    public OidcClients.Client basic(String authorization) {
        return authenticate(authorization, null, null);
    }

    public OidcClients.Client authenticate(String authorization, String bodyId, String bodySecret) {
        String id;
        String secret;
        boolean basic = authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6);
        if (basic && bodySecret != null) {
            throw ClientApiException.invalidRequest("Use one client authentication method");
        }
        if (basic) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(authorization.substring(6).strip()), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw ClientApiException.invalidClient();
            }
            int colon = decoded.indexOf(':');
            if (colon <= 0) {
                throw ClientApiException.invalidClient();
            }
            id = formDecode(decoded.substring(0, colon));
            secret = formDecode(decoded.substring(colon + 1));
            if (bodyId != null && !bodyId.equals(id)) {
                throw ClientApiException.invalidRequest("client_id does not match the authenticated client");
            }
        } else if (bodyId != null && bodySecret != null) {
            id = bodyId;
            secret = bodySecret;
        } else {
            throw ClientApiException.invalidClient();
        }
        if (!limiter.tryAcquire(id)) {
            log.warn("OIDC: client {} is over its request limit", printable(id));
            throw new ClientApiException(HttpStatus.TOO_MANY_REQUESTS, "invalid_request", "Too many requests, slow down");
        }
        return clients.authenticate(id, secret).orElseThrow(() -> {
            log.info("OIDC: client authentication failed for client id {}", printable(id));
            return ClientApiException.invalidClient();
        });
    }

    private static String formDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw ClientApiException.invalidClient();
        }
    }

    /** A client id for the log: no control characters, at most 100. */
    static String printable(String id) {
        String s = id.replaceAll("[^A-Za-z0-9._-]", "?");
        return s.length() > 100 ? s.substring(0, 100) : s;
    }
}
