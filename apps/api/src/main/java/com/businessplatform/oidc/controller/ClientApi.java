package com.businessplatform.oidc.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller called by a client server (not the web app): its errors are RFC 6749 JSON
 * ({@link ClientApiErrors}), never the platform envelope.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ClientApi {
}
