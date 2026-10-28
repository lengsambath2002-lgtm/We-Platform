package com.businessplatform.oidc.service;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * {@code platform.oidc.*}: the platform as an OpenID Connect provider (docs/integrations/virtualbiz-sso.md).
 *
 * <ul>
 * <li>{@code signingKey} ({@code PLATFORM_OIDC_SIGNING_KEY}): an RSA private key (2048 bits or more), PKCS#8 PEM,
 * base64-encoded. Without it a key is generated at start-up only when {@code platform.environment=local}.</li>
 * <li>{@code clientsJson} ({@code PLATFORM_OIDC_CLIENTS}): the registered clients as a JSON array, e.g.
 * {@code [{"id":"virtualbiz","name":"VirtualBiz","secretHash":"<sha-256 hex of the secret>",
 * "redirectUris":["https://shop.example.com/platform/callback"]}]}.</li>
 * <li>{@code clients}: the same list written in YAML ({@code platform.oidc.clients[0].id} …); both are used.</li>
 * </ul>
 */
@ConfigurationProperties("platform.oidc")
public record OidcProperties(String signingKey, String clientsJson, List<Client> clients) {

    public OidcProperties {
        clients = clients == null ? List.of() : List.copyOf(clients);
    }

    /** A registered client: the secret is configured only as its SHA-256 (64 hex characters). */
    public record Client(String id, String name, @JsonAlias("secret-hash") String secretHash,
            @JsonAlias("redirect-uris") List<String> redirectUris) {
    }
}
