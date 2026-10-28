package com.businessplatform.oidc.service;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * A refusal on a server-to-server endpoint (token, shop links), answered as an RFC 6749 §5.2 error
 * {@code {"error": "...", "error_description": "..."}} instead of the platform envelope.
 */
@Getter
public class ClientApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final String description;

    public ClientApiException(HttpStatus status, String error, String description) {
        super(error + ": " + description);
        this.status = status;
        this.error = error;
        this.description = description;
    }

    public static ClientApiException invalidRequest(String description) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, "invalid_request", description);
    }

    public static ClientApiException invalidClient() {
        return new ClientApiException(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed");
    }

    public static ClientApiException invalidGrant(String description) {
        return new ClientApiException(HttpStatus.BAD_REQUEST, "invalid_grant", description);
    }

    public static ClientApiException notFound(String description) {
        return new ClientApiException(HttpStatus.NOT_FOUND, "not_found", description);
    }

    public static ClientApiException forbidden(String description) {
        return new ClientApiException(HttpStatus.FORBIDDEN, "forbidden", description);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this; // An expected answer, not a fault.
    }
}
