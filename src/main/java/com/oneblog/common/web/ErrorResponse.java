package com.oneblog.common.web;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 공통 오류 응답 (contracts/auth-api.md "공통 오류 형식"). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        Integer remainingAttempts,
        Long retryAfterSeconds,
        String limitType,
        Integer limit) {

    public ErrorResponse(String code, String message, List<FieldError> fieldErrors,
            Integer remainingAttempts, Long retryAfterSeconds) {
        this(code, message, fieldErrors, remainingAttempts, retryAfterSeconds, null, null);
    }

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, null, null);
    }

    public record FieldError(String field, String message) {
    }
}
