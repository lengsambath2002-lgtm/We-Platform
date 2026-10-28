package com.businessplatform.oidc.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.businessplatform.oidc.service.ClientApiException;

import lombok.extern.slf4j.Slf4j;

/** RFC 6749 §5.2 error bodies for {@link ClientApi} controllers. */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = ClientApi.class)
public class ClientApiErrors {

    @ExceptionHandler(ClientApiException.class)
    public ResponseEntity<Map<String, String>> refused(ClientApiException e) {
        var res = ResponseEntity.status(e.getStatus()).cacheControl(CacheControl.noStore());
        if (e.getStatus() == HttpStatus.UNAUTHORIZED) {
            res.header("WWW-Authenticate", "Basic realm=\"business-platform\"");
        }
        return res.body(body(e.getError(), e.getDescription()));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class })
    public ResponseEntity<Map<String, String>> malformed(Exception e) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(body("invalid_request", "Malformed request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        log.error("Unexpected error on a client API", e);
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
                .body(body("server_error", "Unexpected error"));
    }

    private static Map<String, String> body(String error, String description) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("error_description", description);
        return m;
    }
}
