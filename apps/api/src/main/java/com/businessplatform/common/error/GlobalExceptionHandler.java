package com.businessplatform.common.error;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.businessplatform.common.api.ApiError;
import com.businessplatform.common.api.ApiResponse;

import lombok.extern.slf4j.Slf4j;

/** Turns every failure into the envelope with an error code; never leaks stack traces. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return respond(e.getCode(), e.getMessage(), e.getFields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage())).toList();
        return respond(ErrorCode.VALIDATION_FAILED, "Validation failed", fields);
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ApiResponse<Void>> unreadable(Exception e) {
        return respond(ErrorCode.VALIDATION_FAILED, "Malformed request", List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException e) {
        return respond(ErrorCode.VALIDATION_FAILED, "File too large",
                List.of(new ApiError.FieldError("file", "At most 20 MB")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException e) {
        return respond(ErrorCode.FORBIDDEN, "Forbidden", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> noRoute(NoResourceFoundException e) {
        return respond(ErrorCode.NOT_FOUND, "Not found", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        log.error("Unexpected error", e);
        return respond(ErrorCode.INTERNAL_ERROR, "Unexpected error", List.of());
    }

    private static ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message,
            List<ApiError.FieldError> fields) {
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(code.name(), new ApiError(message, fields)));
    }
}
