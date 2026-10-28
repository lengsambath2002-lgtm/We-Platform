package com.businessplatform.oidc.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.extern.slf4j.Slf4j;

/**
 * The RS256 key that signs ID tokens: {@code PLATFORM_OIDC_SIGNING_KEY} (PKCS#8 PEM, base64-encoded), or a key
 * generated at start-up when {@code platform.environment=local} (ID tokens then stop verifying after a restart).
 * Elsewhere without a key, OpenID Connect is off; production with clients refuses to start
 * ({@link com.businessplatform.common.security.ProductionSafety}), other environments with clients too.
 */
@Slf4j
@Component
public class OidcKeys {

    private final RSAKey key;

    public OidcKeys(OidcProperties props, OidcClients clients, @Value("${platform.environment:local}") String environment) {
        String configured = props.signingKey();
        if (configured != null && !configured.isBlank()) {
            this.key = parse(configured.strip());
            log.info("OIDC: signing key {} loaded from PLATFORM_OIDC_SIGNING_KEY", key.getKeyID());
        } else if ("local".equalsIgnoreCase(environment)) {
            this.key = generate();
            log.info("OIDC: PLATFORM_OIDC_SIGNING_KEY is not set; generated a temporary signing key {} "
                    + "(local only: ID tokens stop verifying after a restart)", key.getKeyID());
        } else {
            this.key = null;
            if (clients.anyConfigured() && !"production".equalsIgnoreCase(environment)) {
                throw new IllegalStateException("OIDC clients are configured but PLATFORM_OIDC_SIGNING_KEY is not set "
                        + "(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 | base64 | tr -d '\\n')");
            }
        }
    }

    public boolean available() {
        return key != null;
    }

    /** The public key set for {@code /api/oidc/jwks} (no private parts). */
    public Map<String, Object> publicJwks() {
        return key == null ? new JWKSet().toJSONObject(true) : new JWKSet(key.toPublicJWK()).toJSONObject(true);
    }

    /** A signed JWT (RS256, header {@code kid} and {@code typ=JWT}). */
    public String sign(JWTClaimsSet claims) {
        if (key == null) {
            throw new IllegalStateException("No OIDC signing key");
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
                    .type(JOSEObjectType.JWT).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign the ID token", e);
        }
    }

    /** Base64 of a PKCS#8 PEM (or the PEM itself); the key must be RSA with 2048 bits or more. */
    static RSAKey parse(String value) {
        try {
            String pem = value.startsWith("-----BEGIN")
                    ? value
                    : new String(Base64.getMimeDecoder().decode(value), StandardCharsets.UTF_8);
            if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                throw new IllegalStateException("PLATFORM_OIDC_SIGNING_KEY must be PKCS#8 (BEGIN PRIVATE KEY), "
                        + "not PKCS#1: convert it with openssl pkcs8 -topk8 -nocrypt");
            }
            String body = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey priv = (RSAPrivateCrtKey) kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (priv.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("PLATFORM_OIDC_SIGNING_KEY must be an RSA key of 2048 bits or more");
            }
            RSAPublicKey pub = (RSAPublicKey) kf.generatePublic(new RSAPublicKeySpec(priv.getModulus(),
                    priv.getPublicExponent()));
            return build(pub, priv);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // Never echo the value: it is a private key.
            throw new IllegalStateException("PLATFORM_OIDC_SIGNING_KEY is not a base64-encoded PKCS#8 RSA private key");
        }
    }

    private static RSAKey generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            var pair = g.generateKeyPair();
            return build((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate an OIDC signing key", e);
        }
    }

    private static RSAKey build(RSAPublicKey pub, RSAPrivateCrtKey priv) throws JOSEException {
        return new RSAKey.Builder(pub).privateKey(priv).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256)
                .keyIDFromThumbprint().build();
    }
}
