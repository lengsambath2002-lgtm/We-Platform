package com.businessplatform.oidc.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;
import com.businessplatform.common.persistence.Ids;
import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.UserSession;
import com.businessplatform.identity.repository.UserSessionRepository;
import com.businessplatform.oidc.domain.OidcCode;
import com.businessplatform.oidc.domain.OidcConsent;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeCheck;
import com.businessplatform.oidc.payload.OidcPayloads.AuthorizeParams;
import com.businessplatform.oidc.repository.OidcCodeRepository;
import com.businessplatform.oidc.repository.OidcConsentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The authorize step (contract §2): validates the request, remembers consent per user + client, and issues
 * single-use codes (random 32 bytes, stored hashed, 60 seconds). An unknown client or unregistered redirect URI
 * never redirects; every other problem goes back to the client as {@code ?error=…&state=…}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OidcAuthorizeService {

    public static final Duration CODE_TTL = Duration.ofSeconds(60);
    public static final Set<String> SCOPES = Set.of("openid", "email", "profile", "orgs");
    static final int MAX_PARAM = 255;
    /** BASE64URL(SHA-256(verifier)) is always 43 characters. */
    private static final Pattern S256_CHALLENGE = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final OidcClients clients;
    private final OidcKeys keys;
    private final OidcCodeRepository codes;
    private final OidcConsentRepository consents;
    private final UserSessionRepository sessions;
    private final Clock clock;

    /** A checked request: either a reason to refuse without redirecting, an error redirect, or good to go. */
    private record Checked(String invalid, String errorRedirect, OidcClients.Client client, List<String> scopes) {
    }

    /** What the page should do next ({@code userId} null when the visitor is not signed in). */
    @Transactional(readOnly = true)
    public AuthorizeCheck check(AuthorizeParams p, UUID userId) {
        Checked c = validate(p);
        if (c.invalid() != null) {
            return new AuthorizeCheck("INVALID", c.invalid(), null, null, List.of(), null);
        }
        String id = c.client().id();
        String name = c.client().name();
        if (c.errorRedirect() != null) {
            return new AuthorizeCheck("REDIRECT", null, id, name, c.scopes(), c.errorRedirect());
        }
        boolean none = isPromptNone(p);
        if (userId == null) {
            return none ? new AuthorizeCheck("REDIRECT", null, id, name, c.scopes(), error(p, "login_required"))
                    : new AuthorizeCheck("SIGN_IN", null, id, name, c.scopes(), null);
        }
        if (covered(userId, id, c.scopes())) {
            return new AuthorizeCheck("CONTINUE", null, id, name, c.scopes(), null);
        }
        return none ? new AuthorizeCheck("REDIRECT", null, id, name, c.scopes(), error(p, "consent_required"))
                : new AuthorizeCheck("CONSENT", null, id, name, c.scopes(), null);
    }

    /** The signed-in person's decision; the URL to send the browser to. */
    @Transactional
    public String decide(AuthorizeParams p, boolean approve, UUID userId, UUID sessionId) {
        Checked c = validate(p);
        if (c.invalid() != null) {
            throw BusinessException.field(ErrorCode.VALIDATION_FAILED, "client_id",
                    "This app or its return address is not registered with the platform");
        }
        if (c.errorRedirect() != null) {
            return c.errorRedirect();
        }
        if (!approve) {
            log.info("OIDC: user {} cancelled sign-in to client {}", userId, c.client().id());
            return error(p, "access_denied");
        }
        Instant now = clock.instant();
        if (!covered(userId, c.client().id(), c.scopes())) {
            if (isPromptNone(p)) {
                return error(p, "consent_required");
            }
            OidcConsent consent = consents.findByUserIdAndClientId(userId, c.client().id()).orElseGet(() -> {
                OidcConsent n = new OidcConsent();
                n.setId(Ids.next());
                n.setUserId(userId);
                n.setClientId(c.client().id());
                n.setGrantedAt(now);
                n.setScopes("");
                return n;
            });
            Set<String> granted = new LinkedHashSet<>(split(consent.getScopes()));
            granted.addAll(c.scopes());
            consent.setScopes(String.join(" ", granted));
            consent.setUpdatedAt(now);
            consents.save(consent);
            log.info("OIDC: user {} allowed client {} ({})", userId, c.client().id(), consent.getScopes());
        }
        codes.purge(now.minus(Duration.ofDays(1)));
        String code = Tokens.random();
        OidcCode row = new OidcCode();
        row.setId(Ids.next());
        row.setCodeHash(Tokens.sha256(code));
        row.setClientId(c.client().id());
        row.setRedirectUri(p.redirectUri());
        row.setUserId(userId);
        row.setCodeChallenge(p.codeChallenge());
        row.setNonce(p.nonce());
        row.setScope(String.join(" ", c.scopes()));
        row.setAuthTime(authTime(sessionId, now));
        row.setExpiresAt(now.plus(CODE_TTL));
        row.setCreatedAt(now);
        codes.save(row);
        log.info("OIDC: issued an authorization code to client {} for user {}", c.client().id(), userId);
        return p.redirectUri() + (p.redirectUri().contains("?") ? "&" : "?") + "code=" + enc(code)
                + "&state=" + enc(p.state());
    }

    private Checked validate(AuthorizeParams p) {
        if (!keys.available()) {
            return new Checked("invalid_client", null, null, List.of());
        }
        OidcClients.Client client = clients.find(p.clientId()).orElse(null);
        if (client == null) {
            return new Checked("invalid_client", null, null, List.of());
        }
        if (!client.allows(p.redirectUri())) {
            return new Checked("invalid_redirect_uri", null, client, List.of());
        }
        List<String> scopes = split(p.scope());
        String problem = null;
        if (!"code".equals(p.responseType())) {
            problem = "unsupported_response_type";
        } else if (!ok(p.state()) || !ok(p.nonce())) {
            problem = "invalid_request";
        } else if (!"S256".equals(p.codeChallengeMethod()) || p.codeChallenge() == null
                || !S256_CHALLENGE.matcher(p.codeChallenge()).matches()) {
            problem = "invalid_request";
        } else if (!scopes.contains("openid") || !SCOPES.containsAll(scopes)) {
            problem = "invalid_scope";
        }
        return new Checked(null, problem == null ? null : error(p, problem), client, scopes);
    }

    private boolean covered(UUID userId, String clientId, List<String> scopes) {
        return consents.findByUserIdAndClientId(userId, clientId)
                .map(c -> split(c.getScopes()).containsAll(scopes)).orElse(false);
    }

    /** When the person signed in on this device (the session's start), else now. */
    private Instant authTime(UUID sessionId, Instant now) {
        return sessionId == null ? now : sessions.findById(sessionId).map(UserSession::getStartedAt).orElse(now);
    }

    private static boolean isPromptNone(AuthorizeParams p) {
        return p.prompt() != null && split(p.prompt()).contains("none");
    }

    private static boolean ok(String v) {
        return v != null && !v.isEmpty() && v.length() <= MAX_PARAM;
    }

    /** {@code redirect_uri?error=…&state=…} (state only when it is usable). */
    private static String error(AuthorizeParams p, String error) {
        String url = p.redirectUri() + (p.redirectUri().contains("?") ? "&" : "?") + "error=" + error;
        return ok(p.state()) ? url + "&state=" + enc(p.state()) : url;
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static List<String> split(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(scopes.strip().split("\\s+")).distinct().toList();
    }
}
