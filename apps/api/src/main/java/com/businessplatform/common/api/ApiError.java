package com.businessplatform.common.api;

import java.util.List;

/** What went wrong: a developer-facing message and, for validation, one entry per field. */
public record ApiError(String message, List<FieldError> fields) {

    public record FieldError(String field, String message) {
    }
}
