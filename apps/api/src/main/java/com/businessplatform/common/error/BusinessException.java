package com.businessplatform.common.error;

import java.util.List;

import com.businessplatform.common.api.ApiError;

import lombok.Getter;

/** A refused request with its error code, and optionally the field it is about. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final List<ApiError.FieldError> fields;

    public BusinessException(ErrorCode code) {
        this(code, code.name(), List.of());
    }

    public BusinessException(ErrorCode code, String message, List<ApiError.FieldError> fields) {
        super(message);
        this.code = code;
        this.fields = fields;
    }

    public static BusinessException field(ErrorCode code, String field, String message) {
        return new BusinessException(code, message, List.of(new ApiError.FieldError(field, message)));
    }

    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public static BusinessException forbidden() {
        return new BusinessException(ErrorCode.FORBIDDEN);
    }
}
