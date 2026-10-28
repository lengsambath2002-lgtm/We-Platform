package com.businessplatform.oidc.payload;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** The authorize page's calls, the token response and the account page's connected apps. */
public final class OidcPayloads {

    private OidcPayloads() {
    }

    /** The authorize request's query, as the page received it (names as in OpenID Connect). */
    public record AuthorizeParams(@JsonProperty("response_type") String responseType,
            @JsonProperty("client_id") String clientId, @JsonProperty("redirect_uri") String redirectUri, String scope,
            String state, String nonce, @JsonProperty("code_challenge") String codeChallenge,
            @JsonProperty("code_challenge_method") String codeChallengeMethod, String prompt) {

        public static AuthorizeParams of(Map<String, String> q) {
            return new AuthorizeParams(q.get("response_type"), q.get("client_id"), q.get("redirect_uri"), q.get("scope"),
                    q.get("state"), q.get("nonce"), q.get("code_challenge"), q.get("code_challenge_method"),
                    q.get("prompt"));
        }
    }

    /** The signed-in person allows ({@code approve}) or cancels. */
    public record AuthorizeDecision(@NotNull @Valid AuthorizeParams request, boolean approve) {
    }

    /**
     * What the authorize page does next. {@code outcome}:
     * <ul>
     * <li>{@code INVALID}: unknown client or unregistered redirect URI ({@code error}); show an error, never redirect;</li>
     * <li>{@code REDIRECT}: go to {@code redirectUrl} now (a bad request, {@code login_required}, {@code consent_required});</li>
     * <li>{@code SIGN_IN}: not signed in; sign in and come back;</li>
     * <li>{@code CONSENT}: ask the person (first time for this client, or new scopes);</li>
     * <li>{@code CONTINUE}: already approved; post the decision without asking.</li>
     * </ul>
     */
    public record AuthorizeCheck(String outcome, String error, String clientId, String clientName, List<String> scopes,
            String redirectUrl) {
    }

    public record AuthorizeResult(String redirectUrl) {
    }

    /** RFC 6749 §5.1 (snake_case on the wire). */
    public record TokenResponse(@JsonProperty("id_token") String idToken, @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {
    }

    /** An app the person allowed to sign them in. */
    public record ConnectedApp(String clientId, String name, List<String> scopes, Instant grantedAt) {
    }
}
