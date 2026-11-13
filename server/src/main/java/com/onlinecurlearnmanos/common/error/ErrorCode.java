package com.onlinecurlearnmanos.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    OK(HttpStatus.OK, "OK", "success"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求参数错误"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录或登录已过期"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "资源不存在"),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "资源状态冲突"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器内部错误");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
