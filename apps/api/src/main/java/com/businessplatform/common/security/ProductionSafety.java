package com.businessplatform.common.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.businessplatform.oidc.service.OidcClients;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start a production deployment ({@code platform.environment=production}) with settings
 * that are only safe on a developer's machine: the built-in signing secret, insecure cookies, an
 * {@code http} app URL, or OpenID Connect clients without {@code PLATFORM_OIDC_SIGNING_KEY} (or with {@code http}
 * redirect URIs).
 */
@Component
public class ProductionSafety {

    /** The secret in application.yml, for local use only. */
    static final String LOCAL_SECRET = "bG9jYWwtZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS0wMTIzNDU2Nzg5YWJjZGVm";

    private final AuthProperties auth;
    private final String environment;
    private final String appUrl;
    private final OidcClients oidcClients;
    private final String oidcSigningKey;

    public ProductionSafety(AuthProperties auth, @Value("${platform.environment:local}") String environment,
            @Value("${platform.app-url}") String appUrl, OidcClients oidcClients,
            @Value("${platform.oidc.signing-key:}") String oidcSigningKey) {
        this.auth = auth;
        this.environment = environment;
        this.appUrl = appUrl;
        this.oidcClients = oidcClients;
        this.oidcSigningKey = oidcSigningKey;
    }

    @PostConstruct
    void check() {
        List<String> problems = problems(environment, auth, appUrl);
        problems.addAll(oidcProblems(environment, oidcClients.anyConfigured(),
                oidcSigningKey != null && !oidcSigningKey.isBlank(),
                oidcClients.all().stream().flatMap(c -> c.redirectUris().stream()).toList()));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Unsafe production settings: " + String.join("; ", problems));
        }
    }

    static List<String> problems(String environment, AuthProperties auth, String appUrl) {
        List<String> out = new ArrayList<>();
        if (!"production".equalsIgnoreCase(environment)) {
            return out;
        }
        if (LOCAL_SECRET.equals(auth.jwtSecret())) {
            out.add("set PLATFORM_JWT_SECRET (openssl rand -base64 48)");
        }
        if (!auth.cookieSecure()) {
            out.add("set PLATFORM_COOKIE_SECURE=true");
        }
        if (appUrl == null || !appUrl.startsWith("https://")) {
            out.add("PLATFORM_APP_URL must be an https:// address");
        }
        return out;
    }

    /** With OpenID Connect clients, production needs its own signing key and https redirect URIs. */
    static List<String> oidcProblems(String environment, boolean clientsConfigured, boolean signingKeySet,
            List<String> redirectUris) {
        List<String> out = new ArrayList<>();
        if (!"production".equalsIgnoreCase(environment) || !clientsConfigured) {
            return out;
        }
        if (!signingKeySet) {
            out.add("set PLATFORM_OIDC_SIGNING_KEY (PLATFORM_OIDC_CLIENTS is set; see README)");
        }
        if (redirectUris.stream().anyMatch(u -> !u.startsWith("https://"))) {
            out.add("every redirect URI in PLATFORM_OIDC_CLIENTS must be https://");
        }
        return out;
    }
}
