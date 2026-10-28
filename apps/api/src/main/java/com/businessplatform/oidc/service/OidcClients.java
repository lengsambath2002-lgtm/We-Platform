package com.businessplatform.oidc.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.businessplatform.common.security.Tokens;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * The registered OpenID Connect clients ({@link OidcProperties}), checked at start-up, plus the local
 * development client {@value #LOCAL_ID} (secret {@code local-dev-secret}) only when
 * {@code platform.environment=local}. Secrets are compared as SHA-256 hashes in constant time.
 */
@Slf4j
@Component
public class OidcClients {

    public static final String LOCAL_ID = "virtualbiz-local";
    static final String LOCAL_SECRET = "local-dev-secret";
    static final List<String> LOCAL_REDIRECTS = List.of("http://localhost:3000/platform/callback",
            "http://localhost:3100/platform/callback");

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern HEX64 = Pattern.compile("[0-9a-f]{64}");

    /** A client as the platform uses it. */
    public record Client(String id, String name, String secretHash, List<String> redirectUris) {

        public boolean allows(String redirectUri) {
            return redirectUri != null && redirectUris.contains(redirectUri);
        }
    }

    private final Map<String, Client> byId;
    /** Clients from configuration (the local development client is not one). */
    private final int configured;

    public OidcClients(OidcProperties props, @Value("${platform.environment:local}") String environment) {
        List<OidcProperties.Client> all = new ArrayList<>(props.clients());
        all.addAll(parseJson(props.clientsJson()));
        Map<String, Client> out = new LinkedHashMap<>();
        for (OidcProperties.Client c : all) {
            Client checked = check(c);
            if (out.put(checked.id(), checked) != null) {
                throw new IllegalStateException("PLATFORM_OIDC_CLIENTS: client '" + checked.id() + "' is listed twice");
            }
        }
        this.configured = out.size();
        if ("local".equalsIgnoreCase(environment) && !out.containsKey(LOCAL_ID)) {
            out.put(LOCAL_ID, new Client(LOCAL_ID, "VirtualBiz", Tokens.sha256(LOCAL_SECRET), LOCAL_REDIRECTS));
            log.info("OIDC: local development client '{}' is registered (platform.environment=local only)", LOCAL_ID);
        }
        this.byId = Map.copyOf(out);
    }

    public Optional<Client> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /** The client when {@code secret} is its secret; empty otherwise (unknown client or wrong secret). */
    public Optional<Client> authenticate(String id, String secret) {
        Optional<Client> c = find(id);
        if (c.isEmpty() || secret == null || secret.isEmpty()) {
            return Optional.empty();
        }
        boolean ok = MessageDigest.isEqual(c.get().secretHash().getBytes(StandardCharsets.US_ASCII),
                Tokens.sha256(secret).getBytes(StandardCharsets.US_ASCII));
        return ok ? c : Optional.empty();
    }

    /** Whether any client comes from configuration (then a signing key is required outside local). */
    public boolean anyConfigured() {
        return configured > 0;
    }

    public Collection<Client> all() {
        return byId.values();
    }

    private static List<OidcProperties.Client> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                    .readValue(json, new TypeReference<List<OidcProperties.Client>>() { });
        } catch (Exception e) {
            // The message may quote the value; it holds only hashes, but keep the log short anyway.
            throw new IllegalStateException("PLATFORM_OIDC_CLIENTS is not a valid JSON array of clients "
                    + "([{\"id\",\"name\",\"secretHash\",\"redirectUris\"}])");
        }
    }

    private static Client check(OidcProperties.Client c) {
        String id = c.id() == null ? "" : c.id().strip();
        if (!ID.matcher(id).matches()) {
            throw new IllegalStateException("PLATFORM_OIDC_CLIENTS: a client id must be 1-100 of A-Z a-z 0-9 . _ -");
        }
        String hash = c.secretHash() == null ? "" : c.secretHash().strip().toLowerCase();
        if (!HEX64.matcher(hash).matches()) {
            throw new IllegalStateException("PLATFORM_OIDC_CLIENTS: client '" + id
                    + "' needs secretHash = the SHA-256 of its secret in hex (64 characters)");
        }
        List<String> uris = c.redirectUris() == null ? List.of() : c.redirectUris().stream().map(String::strip).toList();
        if (uris.isEmpty()) {
            throw new IllegalStateException("PLATFORM_OIDC_CLIENTS: client '" + id + "' needs at least one redirect URI");
        }
        for (String u : uris) {
            if (!validRedirect(u)) {
                throw new IllegalStateException("PLATFORM_OIDC_CLIENTS: client '" + id + "' has an invalid redirect URI "
                        + "(absolute http(s), no fragment): " + u);
            }
        }
        String name = c.name() == null || c.name().isBlank() ? id : c.name().strip();
        return new Client(id, name, hash, List.copyOf(uris));
    }

    static boolean validRedirect(String u) {
        try {
            URI uri = URI.create(u);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawFragment() == null && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
