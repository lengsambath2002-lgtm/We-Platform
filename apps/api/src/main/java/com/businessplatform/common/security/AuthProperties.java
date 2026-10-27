package com.businessplatform.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code platform.auth.*}: token signing secret (Base64, 32+ bytes) and lifetimes. */
@ConfigurationProperties("platform.auth")
public record AuthProperties(String jwtSecret, Duration accessTtl, Duration refreshTtl, boolean cookieSecure) {
}
