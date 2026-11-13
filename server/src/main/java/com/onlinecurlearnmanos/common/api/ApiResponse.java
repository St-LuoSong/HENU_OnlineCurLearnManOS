package com.onlinecurlearnmanos.common.api;

import org.slf4j.MDC;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, MDC.get("traceId"));
    }

    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("traceId"));
    }
}
