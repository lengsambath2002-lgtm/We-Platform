package com.businessplatform.oidc.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.businessplatform.common.security.Tokens;
import com.businessplatform.identity.domain.AppUser;
import com.businessplatform.identity.repository.UserRepository;
import com.businessplatform.oidc.domain.OidcCode;
import com.businessplatform.oidc.payload.OidcPayloads.TokenResponse;
import com.businessplatform.oidc.repository.OidcCodeRepository;
import com.businessplatform.org.domain.Membership;
import com.businessplatform.org.domain.Organisation;
import com.businessplatform.org.repository.MembershipRepository;
import com.businessplatform.org.repository.OrganisationRepository;
import com.nimbusds.jwt.JWTClaimsSet;

import lombok.extern.slf4j.Slf4j;

/**
 * The token endpoint (contract §3): exchanges a code for an RS256 ID token ({@code exp = iat + 300 s}).
 * A code is spent by the first exchange attempt of its own client, whatever the outcome, so a wrong verifier
 * cannot be retried. Never logs codes, verifiers or tokens.
 */
@Slf4j
@Service
public class OidcTokenService {

    public static final Duration ID_TOKEN_TTL = Duration.ofSeconds(300);
    /** RFC 7636 §4.1. */
    private static final Pattern VERIFIER = Pattern.compile("[A-Za-z0-9._~-]{43,128}");

    private final OidcCodeRepository codes;
    private final OidcKeys keys;
    private final UserRepository users;
    private final MembershipRepository memberships;
    private final OrganisationRepository orgs;
    private final Clock clock;
    private final String issuer;

    public OidcTokenService(OidcCodeRepository codes, OidcKeys keys, UserRepository users,
            MembershipRepository memberships, OrganisationRepository orgs, Clock clock,
            @Value("${platform.app-url}") String appUrl) {
        this.codes = codes;
        this.keys = keys;
        this.users = users;
        this.memberships = memberships;
        this.orgs = orgs;
        this.clock = clock;
        this.issuer = issuer(appUrl);
    }

    /** The issuer: the platform's public web origin, without a trailing slash. */
    public static String issuer(String appUrl) {
        return appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    public String issuer() {
        return issuer;
    }

    @Transactional(noRollbackFor = ClientApiException.class)
    public TokenResponse exchange(OidcClients.Client client, String grantType, String code, String redirectUri,
            String verifier) {
        if (grantType == null || grantType.isBlank()) {
            throw ClientApiException.invalidRequest("grant_type is required");
        }
        if (!"authorization_code".equals(grantType)) {
            throw new ClientApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "unsupported_grant_type",
                    "Only authorization_code is supported");
        }
        if (blank(code) || blank(redirectUri) || blank(verifier)) {
            throw ClientApiException.invalidRequest("code, redirect_uri and code_verifier are required");
        }
        OidcCode c = codes.findByCodeHash(Tokens.sha256(code)).filter(x -> x.getClientId().equals(client.id()))
                .orElseThrow(() -> refuse(client, "unknown code"));
        Instant now = clock.instant();
        if (codes.consume(c.getId(), now) == 0) {
            throw refuse(client, "code already used");
        }
        if (!now.isBefore(c.getExpiresAt())) {
            throw refuse(client, "code expired");
        }
        if (!c.getRedirectUri().equals(redirectUri)) {
            throw refuse(client, "redirect_uri mismatch");
        }
        if (!VERIFIER.matcher(verifier).matches() || !MessageDigest.isEqual(
                s256(verifier).getBytes(StandardCharsets.US_ASCII),
                c.getCodeChallenge().getBytes(StandardCharsets.US_ASCII))) {
            throw refuse(client, "PKCE verification failed");
        }
        AppUser u = users.findById(c.getUserId()).orElseThrow(() -> refuse(client, "user gone"));
        String idToken = keys.sign(claims(client.id(), u, c, now));
        log.info("OIDC: issued an ID token to client {} for user {}", client.id(), u.getId());
        return new TokenResponse(idToken, "Bearer", ID_TOKEN_TTL.toSeconds());
    }

    private JWTClaimsSet claims(String clientId, AppUser u, OidcCode c, Instant now) {
        List<String> scopes = OidcAuthorizeService.split(c.getScope());
        JWTClaimsSet.Builder b = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(clientId)
                .subject(u.getId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ID_TOKEN_TTL)))
                .claim("auth_time", c.getAuthTime().getEpochSecond())
                .claim("nonce", c.getNonce());
        if (scopes.contains("email")) {
            b.claim("email", u.getEmail().toLowerCase()).claim("email_verified", false);
        }
        if (scopes.contains("profile")) {
            b.claim("name", u.getName()).claim("locale", "km".equals(u.getLocale()) ? "km" : "en");
        }
        if (scopes.contains("orgs")) {
            b.claim("orgs", orgs(u.getId()));
        }
        return b.build();
    }

    /** Active memberships of organisations that are not deleted, by name. */
    List<Map<String, Object>> orgs(UUID userId) {
        List<Membership> ms = memberships.findByUserIdAndStatus(userId, Membership.ACTIVE);
        Map<UUID, Organisation> byId = orgs.findAllById(ms.stream().map(Membership::getOrgId).toList()).stream()
                .filter(o -> !o.isDeleted()).collect(Collectors.toMap(Organisation::getId, Function.identity()));
        List<Map<String, Object>> out = new ArrayList<>();
        ms.stream().filter(m -> byId.containsKey(m.getOrgId()))
                .sorted(Comparator.comparing(m -> byId.get(m.getOrgId()).getName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(m -> {
                    Organisation o = byId.get(m.getOrgId());
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("id", o.getId().toString());
                    e.put("slug", o.getSlug());
                    e.put("name", o.getName());
                    e.put("role", m.getRole().name());
                    out.add(e);
                });
        return out;
    }

    private static ClientApiException refuse(OidcClients.Client client, String why) {
        log.info("OIDC: refused a code exchange by client {}: {}", client.id(), why);
        return ClientApiException.invalidGrant("The code is invalid, expired, already used, or does not match");
    }

    static String s256(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
