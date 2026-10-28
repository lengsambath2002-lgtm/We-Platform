package com.businessplatform.common.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Refuses to start a production deployment ({@code platform.environment=production}) with settings
 * that are only safe on a developer's machine: the built-in signing secret, insecure cookies, or an
 * {@code http} app URL.
 */
@Component
public class ProductionSafety {

    /** The secret in application.yml, for local use only. */
    static final String LOCAL_SECRET = "bG9jYWwtZGV2LW9ubHktc2VjcmV0LWNoYW5nZS1tZS0wMTIzNDU2Nzg5YWJjZGVm";

    private final AuthProperties auth;
    private final String environment;
    private final String appUrl;

    public ProductionSafety(AuthProperties auth, @Value("${platform.environment:local}") String environment,
            @Value("${platform.app-url}") String appUrl) {
        this.auth = auth;
        this.environment = environment;
        this.appUrl = appUrl;
    }

    @PostConstruct
    void check() {
        List<String> problems = problems(environment, auth, appUrl);
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
}
