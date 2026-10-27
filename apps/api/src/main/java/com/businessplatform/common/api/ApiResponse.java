package com.businessplatform.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Every answer: {@code {status: {code}, data, error}}. Clients translate codes; messages are for developers. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(Status status, T data, ApiError error) {

    public record Status(String code) {
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(new Status("SUCCESS"), data, null);
    }

    public static ApiResponse<Void> fail(String code, ApiError error) {
        return new ApiResponse<>(new Status(code), null, error);
    }
}
