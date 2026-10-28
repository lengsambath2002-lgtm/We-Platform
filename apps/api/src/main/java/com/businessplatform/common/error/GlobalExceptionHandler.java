package com.businessplatform.common.error;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.businessplatform.common.api.ApiError;
import com.businessplatform.common.api.ApiResponse;
import com.businessplatform.common.i18n.Messages;

import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns every failure into the envelope with an error code; never leaks stack traces. Messages are
 * translated to the request language ({@code Accept-Language}); Bean Validation defaults get a short
 * generic text per constraint.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Messages messages;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return respond(e.getCode(), e.getMessage(), e.getFields());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.FieldError(f.getField(), fieldMessage(f))).toList();
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

    private ResponseEntity<ApiResponse<Void>> respond(ErrorCode code, String message,
            List<ApiError.FieldError> fields) {
        List<ApiError.FieldError> translated = fields.stream()
                .map(f -> new ApiError.FieldError(f.field(), messages.text(f.message()))).toList();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.fail(code.name(), new ApiError(messages.text(message), translated)));
    }

    /** A custom {@code message = "..."} is translated as is; a default one becomes a generic text per constraint. */
    private String fieldMessage(FieldError f) {
        if (!f.contains(ConstraintViolation.class)) {
            return messages.text("Not a valid value");
        }
        ConstraintViolation<?> v = f.unwrap(ConstraintViolation.class);
        String template = v.getMessageTemplate();
        if (!template.startsWith("{")) {
            return messages.text(template);
        }
        Map<String, Object> a = v.getConstraintDescriptor().getAttributes();
        String code = f.getCode() == null ? "" : f.getCode();
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> messages.text("Required");
            case "Size" -> size(a, isMany(f.getRejectedValue()));
            case "Email" -> messages.text("Not a valid email address");
            case "Min", "DecimalMin" -> messages.text("At least {value}", Map.of("value", a.get("value")));
            case "Max", "DecimalMax" -> messages.text("At most {value}", Map.of("value", a.get("value")));
            case "Positive" -> messages.text("Must be more than 0");
            case "PositiveOrZero" -> messages.text("Must be 0 or more");
            case "Future", "FutureOrPresent" -> messages.text("Must be in the future");
            case "Past", "PastOrPresent" -> messages.text("Must be in the past");
            default -> messages.text("Not a valid value");
        };
    }

    private String size(Map<String, Object> a, boolean many) {
        int min = (Integer) a.getOrDefault("min", 0);
        int max = (Integer) a.getOrDefault("max", Integer.MAX_VALUE);
        Map<String, Object> args = Map.of("min", min, "max", max);
        if (min > 0 && max < Integer.MAX_VALUE) {
            return messages.text(many ? "{min}–{max} items" : "{min}–{max} characters", args);
        }
        if (min > 0) {
            return messages.text(many ? "At least {min} items" : "At least {min} characters", args);
        }
        return messages.text(many ? "At most {max} items" : "At most {max} characters", args);
    }

    private static boolean isMany(Object value) {
        return value instanceof Collection<?> || value instanceof Map<?, ?>
                || value != null && value.getClass().isArray();
    }
}
