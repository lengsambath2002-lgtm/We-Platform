package com.businessplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.businessplatform.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

/**
 * The platform as the OpenID Connect provider for VirtualBiz (docs/integrations/virtualbiz-sso.md): code flow with
 * PKCE, consent, the ID token and its claims, every refusal, and the shop-link API. The client, its secret and the
 * signing key are generated for this run.
 */
class OidcTest extends IntegrationTest {

    static final String CLIENT = "vbiz-test";
    static final String SECRET = random(32);
    static final String REDIRECT = "https://shop.test/platform/callback";
    static final String OTHER_CLIENT = "other-app";
    static final String OTHER_SECRET = random(32);

    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry r) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(g.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        r.add("platform.oidc.signing-key", () -> Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8)));
        r.add("platform.oidc.clients-json", () -> "[{\"id\":\"" + CLIENT + "\",\"name\":\"VirtualBiz\",\"secretHash\":\""
                + sha256(SECRET) + "\",\"redirectUris\":[\"" + REDIRECT + "\",\"https://shop.test/other\"]},"
                + "{\"id\":\"" + OTHER_CLIENT + "\",\"name\":\"Other\",\"secretHash\":\"" + sha256(OTHER_SECRET)
                + "\",\"redirectUris\":[\"https://other.test/cb\"]}]");
        r.add("platform.virtualbiz.url", () -> "https://shop.test/");
    }

    @Autowired
    JdbcTemplate jdbc;

    // ── Helpers ──────────────────────────────────────────────────────────────────────

    record Pkce(String verifier, String challenge) {
        static Pkce create() throws Exception {
            String v = random(48);
            return new Pkce(v, Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.US_ASCII))));
        }
    }

    static Map<String, String> params(Pkce pkce, String nonce) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("response_type", "code");
        p.put("client_id", CLIENT);
        p.put("redirect_uri", REDIRECT);
        p.put("scope", "openid email profile orgs");
        p.put("state", "st-" + random(8));
        p.put("nonce", nonce);
        p.put("code_challenge", pkce.challenge());
        p.put("code_challenge_method", "S256");
        return p;
    }

    JsonNode check(User u, Map<String, String> p) throws Exception {
        var req = get("/api/oidc/authorize/check");
        p.forEach(req::queryParam);
        if (u != null) {
            req.header("Authorization", "Bearer " + u.token());
        }
        return json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString()).path("data");
    }

    String decide(User u, Map<String, String> p, boolean approve) throws Exception {
        return ok(call(u, HttpMethod.POST, "/api/oidc/authorize", Map.of("request", p, "approve", approve)))
                .path("data").path("redirectUrl").asText();
    }

    static Map<String, String> query(String url) {
        Map<String, String> out = new HashMap<>();
        String q = URI.create(url).getRawQuery();
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            out.put(kv.substring(0, i), URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    /** Signs in through the code flow and returns the redirect's code. */
    String code(User u, Map<String, String> p) throws Exception {
        String url = decide(u, p, true);
        assertThat(url).startsWith(REDIRECT + "?");
        Map<String, String> q = query(url);
        assertThat(q.get("state")).isEqualTo(p.get("state"));
        return q.get("code");
    }

    record Res(int status, JsonNode body, String wwwAuthenticate, String cacheControl) {
    }

    Res token(String basicId, String basicSecret, Map<String, String> form) throws Exception {
        var req = post("/api/oidc/token").contentType(MediaType.APPLICATION_FORM_URLENCODED);
        form.forEach(req::param);
        if (basicId != null) {
            req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (basicId + ":" + basicSecret).getBytes(StandardCharsets.UTF_8)));
        }
        var res = mvc.perform(req).andReturn().getResponse();
        String text = res.getContentAsString();
        return new Res(res.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text),
                res.getHeader("WWW-Authenticate"), res.getHeader("Cache-Control"));
    }

    Res exchange(String code, String redirect, String verifier) throws Exception {
        return token(CLIENT, SECRET, Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", redirect,
                "code_verifier", verifier));
    }

    Res link(String id, String secret, String businessId, Object body, HttpMethod method) throws Exception {
        var req = request(method, "/api/integrations/virtualbiz/links/" + businessId);
        if (id != null) {
            req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (id + ":" + secret).getBytes(StandardCharsets.UTF_8)));
        }
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        var res = mvc.perform(req).andReturn().getResponse();
        String text = res.getContentAsString();
        return new Res(res.getStatus(), text.isBlank() ? json.createObjectNode() : json.readTree(text), null, null);
    }

    static String random(int bytes) {
        byte[] b = new byte[bytes];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── Discovery ────────────────────────────────────────────────────────────────────

    @Test
    void discovery_and_jwks_are_public() throws Exception {
        JsonNode d = json.readTree(mvc.perform(get("/api/oidc/.well-known/openid-configuration")).andReturn()
                .getResponse().getContentAsString());
        assertThat(d.path("issuer").asText()).isEqualTo("http://localhost:3300");
        assertThat(d.path("authorization_endpoint").asText()).isEqualTo("http://localhost:3300/oauth/authorize");
        assertThat(d.path("token_endpoint").asText()).isEqualTo("http://localhost:3300/api/oidc/token");
        assertThat(d.path("jwks_uri").asText()).isEqualTo("http://localhost:3300/api/oidc/jwks");
        assertThat(d.path("code_challenge_methods_supported").toString()).isEqualTo("[\"S256\"]");
        assertThat(d.path("token_endpoint_auth_methods_supported").toString())
                .isEqualTo("[\"client_secret_basic\",\"client_secret_post\"]");
        JsonNode k = json.readTree(mvc.perform(get("/api/oidc/jwks")).andReturn().getResponse().getContentAsString())
                .path("keys").get(0);
        assertThat(k.path("kty").asText()).isEqualTo("RSA");
        assertThat(k.path("use").asText()).isEqualTo("sig");
        assertThat(k.path("alg").asText()).isEqualTo("RS256");
        assertThat(k.path("kid").asText()).isNotBlank();
        assertThat(k.has("n") && k.has("e")).isTrue();
        assertThat(k.has("d")).as("no private part").isFalse();
    }

    // ── The code flow ────────────────────────────────────────────────────────────────

    @Test
    void full_code_flow_with_pkce_issues_a_verifiable_id_token() throws Exception {
        User u = signUp("Sokha Dara");
        String org = newOrg(u);
        Pkce pkce = Pkce.create();
        Map<String, String> p = params(pkce, "n-" + random(8));

        assertThat(check(null, p).path("outcome").asText()).isEqualTo("SIGN_IN");
        JsonNode first = check(u, p);
        assertThat(first.path("outcome").asText()).isEqualTo("CONSENT");
        assertThat(first.path("clientName").asText()).isEqualTo("VirtualBiz");

        Instant before = Instant.now().minusSeconds(1);
        String code = code(u, p);
        assertThat(code).hasSizeGreaterThanOrEqualTo(43);
        Res r = exchange(code, REDIRECT, pkce.verifier());
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.cacheControl()).contains("no-store");
        assertThat(r.body().path("token_type").asText()).isEqualTo("Bearer");
        assertThat(r.body().path("expires_in").asInt()).isEqualTo(300);

        SignedJWT jwt = SignedJWT.parse(r.body().path("id_token").asText());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        JWKSet jwks = JWKSet.parse(mvc.perform(get("/api/oidc/jwks")).andReturn().getResponse().getContentAsString());
        RSAKey key = (RSAKey) jwks.getKeyByKeyId(jwt.getHeader().getKeyID());
        assertThat(key).as("kid found in the JWKS").isNotNull();
        assertThat(jwt.verify(new RSASSAVerifier(key))).isTrue();

        var c = jwt.getJWTClaimsSet();
        assertThat(c.getIssuer()).isEqualTo("http://localhost:3300");
        assertThat(c.getAudience()).containsExactly(CLIENT);
        assertThat(c.getSubject()).isEqualTo(u.id().toString());
        assertThat(c.getExpirationTime().getTime() - c.getIssueTime().getTime()).isEqualTo(300_000);
        assertThat(c.getIssueTime().toInstant()).isAfter(before);
        long authTime = c.getLongClaim("auth_time");
        assertThat(authTime).isBetween(before.getEpochSecond() - 120, Instant.now().getEpochSecond());
        assertThat(c.getStringClaim("nonce")).isEqualTo(p.get("nonce"));
        assertThat(c.getStringClaim("email")).isEqualTo(u.email().toLowerCase());
        assertThat(c.getBooleanClaim("email_verified")).isFalse();
        assertThat(c.getStringClaim("name")).isEqualTo("Sokha Dara");
        assertThat(c.getStringClaim("locale")).isEqualTo("en");
        List<Object> orgs = c.getListClaim("orgs");
        assertThat(orgs).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) orgs.get(0);
        assertThat(o.get("slug")).isEqualTo(org);
        assertThat(o.get("name")).isEqualTo("Org " + org);
        assertThat(o.get("role")).isEqualTo("OWNER");
        assertThat(UUID.fromString((String) o.get("id"))).isNotNull();

        // Body client authentication works too (client_secret_post), on a fresh code.
        Pkce p2 = Pkce.create();
        Map<String, String> again = params(p2, "n2");
        String code2 = code(u, again);
        Res viaBody = token(null, null, Map.of("grant_type", "authorization_code", "code", code2, "redirect_uri", REDIRECT,
                "code_verifier", p2.verifier(), "client_id", CLIENT, "client_secret", SECRET));
        assertThat(viaBody.status()).isEqualTo(200);
    }

    @Test
    void consent_is_remembered_and_revocable() throws Exception {
        User u = signUp("Remember Me");
        Map<String, String> p = params(Pkce.create(), "n");
        assertThat(check(u, p).path("outcome").asText()).isEqualTo("CONSENT");
        code(u, p);
        assertThat(check(u, params(Pkce.create(), "n")).path("outcome").asText()).isEqualTo("CONTINUE");

        JsonNode apps = ok(call(u, HttpMethod.GET, "/api/me/connected-apps", null)).path("data");
        assertThat(apps).hasSize(1);
        assertThat(apps.get(0).path("clientId").asText()).isEqualTo(CLIENT);
        assertThat(apps.get(0).path("name").asText()).isEqualTo("VirtualBiz");
        assertThat(apps.get(0).path("scopes").toString()).contains("orgs");

        ok(call(u, HttpMethod.DELETE, "/api/me/connected-apps/" + CLIENT, null));
        assertThat(ok(call(u, HttpMethod.GET, "/api/me/connected-apps", null)).path("data")).isEmpty();
        assertThat(check(u, params(Pkce.create(), "n")).path("outcome").asText()).isEqualTo("CONSENT");
        assertThat(code(call(u, HttpMethod.DELETE, "/api/me/connected-apps/" + CLIENT, null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void prompt_none_never_shows_ui() throws Exception {
        User u = signUp("Quiet Person");
        Map<String, String> p = params(Pkce.create(), "n");
        p.put("prompt", "none");
        JsonNode anon = check(null, p);
        assertThat(anon.path("outcome").asText()).isEqualTo("REDIRECT");
        assertThat(query(anon.path("redirectUrl").asText())).containsEntry("error", "login_required")
                .containsEntry("state", p.get("state"));
        JsonNode noConsent = check(u, p);
        assertThat(noConsent.path("outcome").asText()).isEqualTo("REDIRECT");
        assertThat(query(noConsent.path("redirectUrl").asText())).containsEntry("error", "consent_required");
        // Even a direct approval with prompt=none does not create consent.
        assertThat(query(decide(u, p, true))).containsEntry("error", "consent_required").doesNotContainKey("code");
        assertThat(ok(call(u, HttpMethod.GET, "/api/me/connected-apps", null)).path("data")).isEmpty();

        code(u, params(Pkce.create(), "n"));
        assertThat(check(u, p).path("outcome").asText()).isEqualTo("CONTINUE");
        assertThat(query(decide(u, p, true))).containsKey("code");
    }

    @Test
    void cancel_redirects_with_access_denied() throws Exception {
        User u = signUp("Says No");
        Map<String, String> p = params(Pkce.create(), "n");
        Map<String, String> q = query(decide(u, p, false));
        assertThat(q).containsEntry("error", "access_denied").containsEntry("state", p.get("state")).doesNotContainKey("code");
    }

    @Test
    void unknown_client_or_redirect_never_redirects() throws Exception {
        User u = signUp("Careful Person");
        Map<String, String> p = params(Pkce.create(), "n");
        p.put("client_id", "nobody");
        JsonNode c = check(u, p);
        assertThat(c.path("outcome").asText()).isEqualTo("INVALID");
        assertThat(c.path("error").asText()).isEqualTo("invalid_client");
        assertThat(c.path("redirectUrl").isNull()).isTrue();
        assertThat(code(call(u, HttpMethod.POST, "/api/oidc/authorize", Map.of("request", p, "approve", true))))
                .isEqualTo("VALIDATION_FAILED");

        Map<String, String> bad = params(Pkce.create(), "n");
        bad.put("redirect_uri", "https://evil.test/platform/callback");
        assertThat(check(u, bad).path("error").asText()).isEqualTo("invalid_redirect_uri");
        bad.put("redirect_uri", REDIRECT + "/");
        assertThat(check(u, bad).path("outcome").asText()).as("exact match only").isEqualTo("INVALID");
        // Another client's redirect URI.
        bad.put("redirect_uri", "https://other.test/cb");
        assertThat(check(u, bad).path("outcome").asText()).isEqualTo("INVALID");
    }

    @Test
    void bad_parameters_go_back_to_the_client_as_errors() throws Exception {
        User u = signUp("Param Person");
        Map<String, String> p = params(Pkce.create(), "n");
        p.put("code_challenge_method", "plain");
        assertThat(query(check(u, p).path("redirectUrl").asText())).containsEntry("error", "invalid_request");
        p = params(Pkce.create(), "n");
        p.remove("nonce");
        assertThat(query(check(u, p).path("redirectUrl").asText())).containsEntry("error", "invalid_request");
        p = params(Pkce.create(), "x".repeat(256));
        assertThat(query(check(u, p).path("redirectUrl").asText())).containsEntry("error", "invalid_request");
        p = params(Pkce.create(), "n");
        p.put("response_type", "token");
        assertThat(query(check(u, p).path("redirectUrl").asText())).containsEntry("error", "unsupported_response_type");
        p = params(Pkce.create(), "n");
        p.put("scope", "email profile");
        assertThat(query(check(u, p).path("redirectUrl").asText())).containsEntry("error", "invalid_scope");
    }

    // ── Token refusals ───────────────────────────────────────────────────────────────

    @Test
    void wrong_verifier_is_refused_and_spends_the_code() throws Exception {
        User u = signUp("Wrong Verifier");
        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        Res r = exchange(code, REDIRECT, Pkce.create().verifier());
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().path("error").asText()).isEqualTo("invalid_grant");
        assertThat(r.body().has("status")).as("not the platform envelope").isFalse();
        assertThat(exchange(code, REDIRECT, pkce.verifier()).body().path("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void a_code_works_once() throws Exception {
        User u = signUp("Reuse Code");
        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        assertThat(exchange(code, REDIRECT, pkce.verifier()).status()).isEqualTo(200);
        Res again = exchange(code, REDIRECT, pkce.verifier());
        assertThat(again.status()).isEqualTo(400);
        assertThat(again.body().path("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void an_expired_code_is_refused() throws Exception {
        User u = signUp("Slow Client");
        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        assertThat(jdbc.queryForObject("SELECT extract(epoch FROM expires_at - created_at) FROM oidc_code WHERE code_hash = ?",
                Double.class, sha256(code))).isEqualTo(60.0);
        jdbc.update("UPDATE oidc_code SET expires_at = now() - interval '1 second' WHERE code_hash = ?", sha256(code));
        assertThat(exchange(code, REDIRECT, pkce.verifier()).body().path("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void codes_are_stored_hashed_only() throws Exception {
        User u = signUp("Hash Check");
        String code = code(u, params(Pkce.create(), "n"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oidc_code WHERE code_hash = ?", Integer.class, code)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM oidc_code WHERE code_hash = ?", Integer.class, sha256(code)))
                .isEqualTo(1);
    }

    @Test
    void the_redirect_uri_must_match_the_authorize_request() throws Exception {
        User u = signUp("Redirect Mismatch");
        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        Res r = exchange(code, "https://shop.test/other", pkce.verifier());
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().path("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void client_authentication_failures_are_401_invalid_client() throws Exception {
        User u = signUp("Bad Secret");
        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        Map<String, String> form = Map.of("grant_type", "authorization_code", "code", code, "redirect_uri", REDIRECT,
                "code_verifier", pkce.verifier());
        Res wrong = token(CLIENT, "not-the-secret", form);
        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.body().path("error").asText()).isEqualTo("invalid_client");
        assertThat(wrong.wwwAuthenticate()).startsWith("Basic");
        Res unknown = token("nobody", SECRET, form);
        assertThat(unknown.status()).isEqualTo(401);
        assertThat(unknown.body().path("error").asText()).isEqualTo("invalid_client");
        assertThat(token(null, null, form).status()).isEqualTo(401);
        // Another registered client cannot spend this client's code.
        Res other = token(OTHER_CLIENT, OTHER_SECRET, form);
        assertThat(other.body().path("error").asText()).isEqualTo("invalid_grant");
        // The failures above did not spend the code.
        assertThat(exchange(code, REDIRECT, pkce.verifier()).status()).isEqualTo(200);
    }

    @Test
    void grant_type_and_missing_parameters() throws Exception {
        Res r = token(CLIENT, SECRET, Map.of("grant_type", "client_credentials"));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().path("error").asText()).isEqualTo("unsupported_grant_type");
        assertThat(token(CLIENT, SECRET, Map.of("grant_type", "authorization_code")).body().path("error").asText())
                .isEqualTo("invalid_request");
        assertThat(token(CLIENT, SECRET, Map.of()).body().path("error").asText()).isEqualTo("invalid_request");
    }

    // ── Organisations in the ID token ────────────────────────────────────────────────

    @Test
    void orgs_claim_lists_only_active_memberships_of_live_organisations() throws Exception {
        User owner = signUp("Many Orgs Owner");
        User u = signUp("Many Orgs Member");
        String active = newOrg(owner);
        String suspended = newOrg(owner);
        String deleted = newOrg(owner);
        join(owner, active, u, "ADMIN");
        join(owner, suspended, u, "MEMBER");
        join(owner, deleted, u, "GUEST");
        String membership = null;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + suspended + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(u.id().toString())) {
                membership = m.path("membershipId").asText();
            }
        }
        ok(call(owner, HttpMethod.PATCH, "/api/orgs/" + suspended + "/members/" + membership, Map.of("status", "SUSPENDED")));
        ok(call(owner, HttpMethod.DELETE, "/api/orgs/" + deleted, Map.of("name", "Org " + deleted)));

        Pkce pkce = Pkce.create();
        String code = code(u, params(pkce, "n"));
        SignedJWT jwt = SignedJWT.parse(exchange(code, REDIRECT, pkce.verifier()).body().path("id_token").asText());
        List<Object> orgs = jwt.getJWTClaimsSet().getListClaim("orgs");
        assertThat(orgs).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> o = (Map<String, Object>) orgs.get(0);
        assertThat(o).containsEntry("slug", active).containsEntry("role", "ADMIN");
    }

    // ── Shop links ───────────────────────────────────────────────────────────────────

    private String orgId(User u, String slug) throws Exception {
        return ok(call(u, HttpMethod.GET, "/api/orgs/" + slug + "/context", null)).path("data").path("org").path("id").asText();
    }

    @Test
    void only_an_owner_or_admin_can_be_linked_by() throws Exception {
        User owner = signUp("Shop Owner");
        User admin = signUp("Shop Admin");
        User member = signUp("Shop Member");
        User outsider = signUp("Shop Outsider");
        String slug = newOrg(owner);
        join(owner, slug, admin, "ADMIN");
        join(owner, slug, member, "MEMBER");
        String orgId = orgId(owner, slug);
        String shop = UUID.randomUUID().toString();

        for (User who : List.of(member, outsider)) {
            Res r = link(CLIENT, SECRET, shop, Map.of("orgId", orgId, "businessName", "Dara Mart", "linkedBy",
                    who.id().toString()), HttpMethod.PUT);
            assertThat(r.status()).isEqualTo(403);
            assertThat(r.body().path("error").asText()).isEqualTo("forbidden");
        }
        Res missingOrg = link(CLIENT, SECRET, shop, Map.of("orgId", UUID.randomUUID().toString(), "businessName", "X",
                "linkedBy", owner.id().toString()), HttpMethod.PUT);
        assertThat(missingOrg.status()).isEqualTo(403);
        Res bad = link(CLIENT, SECRET, shop, Map.of("orgId", orgId), HttpMethod.PUT);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().path("error").asText()).isEqualTo("invalid_request");
        assertThat(link(CLIENT, SECRET, "not-a-uuid", Map.of("orgId", orgId, "businessName", "X", "linkedBy",
                owner.id().toString()), HttpMethod.PUT).status()).isEqualTo(400);

        Res ok = link(CLIENT, SECRET, shop, Map.of("orgId", orgId, "businessName", " Dara Mart ", "linkedBy",
                admin.id().toString()), HttpMethod.PUT);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.body().path("orgId").asText()).isEqualTo(orgId);
        assertThat(ok.body().path("businessId").asText()).isEqualTo(shop);
        assertThat(ok.body().path("businessName").asText()).isEqualTo("Dara Mart");
        assertThat(ok.body().path("linkedAt").asText()).isNotBlank();

        // A suspended admin no longer can.
        String adminMembership = null;
        for (JsonNode m : ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/members", null)).path("data")) {
            if (m.path("userId").asText().equals(admin.id().toString())) {
                adminMembership = m.path("membershipId").asText();
            }
        }
        ok(call(owner, HttpMethod.PATCH, "/api/orgs/" + slug + "/members/" + adminMembership, Map.of("status", "SUSPENDED")));
        assertThat(link(CLIENT, SECRET, UUID.randomUUID().toString(), Map.of("orgId", orgId, "businessName", "Y",
                "linkedBy", admin.id().toString()), HttpMethod.PUT).status()).isEqualTo(403);
    }

    @Test
    void bad_client_credentials_are_401() throws Exception {
        User owner = signUp("Cred Owner");
        String slug = newOrg(owner);
        Map<String, Object> body = Map.of("orgId", orgId(owner, slug), "businessName", "Shop", "linkedBy", owner.id().toString());
        String shop = UUID.randomUUID().toString();
        for (Res r : List.of(link(CLIENT, "wrong", shop, body, HttpMethod.PUT), link(null, null, shop, body, HttpMethod.PUT),
                link("nobody", SECRET, shop, body, HttpMethod.PUT), link(CLIENT, "wrong", shop, null, HttpMethod.DELETE))) {
            assertThat(r.status()).isEqualTo(401);
            assertThat(r.body().path("error").asText()).isEqualTo("invalid_client");
        }
        // A user's access token is not client authentication.
        var res = mvc.perform(request(HttpMethod.PUT, "/api/integrations/virtualbiz/links/" + shop)
                .header("Authorization", "Bearer " + owner.token()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void relinking_moves_the_shop_and_unlinking_removes_it_with_audit_entries() throws Exception {
        User owner = signUp("Mover Owner");
        String a = newOrg(owner);
        String b = newOrg(owner);
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + a + "/modules/retail", Map.of("enabled", true)));
        ok(call(owner, HttpMethod.PUT, "/api/orgs/" + b + "/modules/retail", Map.of("enabled", true)));
        String shop = UUID.randomUUID().toString();
        assertThat(link(CLIENT, SECRET, shop, Map.of("orgId", orgId(owner, a), "businessName", "Riverside Café",
                "linkedBy", owner.id().toString()), HttpMethod.PUT).status()).isEqualTo(200);
        JsonNode shopsA = ok(call(owner, HttpMethod.GET, "/api/orgs/" + a + "/retail/shops", null)).path("data");
        assertThat(shopsA.path("configured").asBoolean()).isTrue();
        assertThat(shopsA.path("virtualbizUrl").asText()).isEqualTo("https://shop.test");
        assertThat(shopsA.path("canManage").asBoolean()).isTrue();
        assertThat(shopsA.path("shops")).hasSize(1);
        assertThat(shopsA.path("shops").get(0).path("businessName").asText()).isEqualTo("Riverside Café");
        assertThat(shopsA.path("shops").get(0).path("linkedBy").path("name").asText()).isEqualTo("Mover Owner");

        // The audit log names the shop.
        JsonNode audit = ok(call(owner, HttpMethod.GET, "/api/orgs/" + a + "/activity?module=retail", null))
                .path("data").path("items");
        assertThat(audit.get(0).path("verb").asText()).isEqualTo("retail.shop.linked");
        assertThat(audit.get(0).path("record").path("title").asText()).isEqualTo("Riverside Café");
        assertThat(audit.get(0).path("record").path("link").asText()).isEqualTo("/" + a + "/retail");

        assertThat(link(CLIENT, SECRET, shop, Map.of("orgId", orgId(owner, b), "businessName", "Riverside Café",
                "linkedBy", owner.id().toString()), HttpMethod.PUT).status()).isEqualTo(200);
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + a + "/retail/shops", null)).path("data").path("shops")).isEmpty();
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + b + "/retail/shops", null)).path("data").path("shops")).hasSize(1);
        // VirtualBiz can confirm the current link before granting access through it.
        Res current = link(CLIENT, SECRET, shop, null, HttpMethod.GET);
        assertThat(current.status()).isEqualTo(200);
        assertThat(current.body().path("orgId").asText()).isEqualTo(orgId(owner, b));
        assertThat(link(CLIENT, "wrong-secret", shop, null, HttpMethod.GET).status()).isEqualTo(401);
        JsonNode auditA = ok(call(owner, HttpMethod.GET, "/api/orgs/" + a + "/activity?module=retail", null)).path("data").path("items");
        assertThat(auditA.get(0).path("verb").asText()).isEqualTo("retail.shop.unlinked");
        assertThat(auditA.get(0).path("data").path("reason").asText()).isEqualTo("moved");

        Res del = link(CLIENT, SECRET, shop, null, HttpMethod.DELETE);
        assertThat(del.status()).isEqualTo(204);
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + b + "/retail/shops", null)).path("data").path("shops")).isEmpty();
        assertThat(link(CLIENT, SECRET, shop, null, HttpMethod.DELETE).status()).as("idempotent").isEqualTo(204);
        Res gone = link(CLIENT, SECRET, shop, null, HttpMethod.GET);
        assertThat(gone.status()).isEqualTo(404);
        assertThat(gone.body().path("error").asText()).isEqualTo("not_found");
        JsonNode auditB = ok(call(owner, HttpMethod.GET, "/api/orgs/" + b + "/activity?module=retail", null)).path("data").path("items");
        assertThat(auditB.get(0).path("verb").asText()).isEqualTo("retail.shop.unlinked");
        assertThat(auditB.get(0).path("data").path("name").asText()).isEqualTo("Riverside Café");
    }

    @Test
    void admins_unlink_on_the_platform_members_only_read_guests_cannot() throws Exception {
        User owner = signUp("Retail Owner");
        User member = signUp("Retail Member");
        User guest = signUp("Retail Guest");
        String slug = newOrg(owner);
        join(owner, slug, member, "MEMBER");
        join(owner, slug, guest, "GUEST");
        // Off until switched on.
        assertThat(code(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/retail/shops", null))).isEqualTo("MODULE_DISABLED");
        JsonNode ctx = ok(call(owner, HttpMethod.PUT, "/api/orgs/" + slug + "/modules/retail", Map.of("enabled", true))).path("data");
        assertThat(ctx.path("permissions").toString()).contains("retail.view", "retail.manage");
        String shop = UUID.randomUUID().toString();
        link(CLIENT, SECRET, shop, Map.of("orgId", orgId(owner, slug), "businessName", "Corner Shop", "linkedBy",
                owner.id().toString()), HttpMethod.PUT);

        JsonNode asMember = ok(call(member, HttpMethod.GET, "/api/orgs/" + slug + "/retail/shops", null)).path("data");
        assertThat(asMember.path("canManage").asBoolean()).isFalse();
        assertThat(asMember.path("shops")).hasSize(1);
        assertThat(code(call(member, HttpMethod.DELETE, "/api/orgs/" + slug + "/retail/shops/" + shop, null))).isEqualTo("FORBIDDEN");
        assertThat(code(call(guest, HttpMethod.GET, "/api/orgs/" + slug + "/retail/shops", null))).isEqualTo("FORBIDDEN");

        ok(call(owner, HttpMethod.DELETE, "/api/orgs/" + slug + "/retail/shops/" + shop, null));
        assertThat(ok(call(owner, HttpMethod.GET, "/api/orgs/" + slug + "/retail/shops", null)).path("data").path("shops")).isEmpty();
        assertThat(code(call(owner, HttpMethod.DELETE, "/api/orgs/" + slug + "/retail/shops/" + shop, null))).isEqualTo("NOT_FOUND");
    }

    @Test
    void retail_is_available_when_virtualbiz_is_configured() throws Exception {
        JsonNode modules = ok(call(signUp("Catalogue Reader"), HttpMethod.GET, "/api/modules", null)).path("data");
        for (JsonNode m : modules) {
            if (m.path("key").asText().equals("retail")) {
                assertThat(m.path("status").asText()).isEqualTo("AVAILABLE");
                return;
            }
        }
        throw new AssertionError("retail missing");
    }
}
