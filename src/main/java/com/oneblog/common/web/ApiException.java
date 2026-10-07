package com.oneblog.common.web;

import org.springframework.http.HttpStatus;

/** 정해진 HTTP 상태와 오류 코드로 응답할 업무 예외. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer remainingAttempts;
    private final Long retryAfterSeconds;
    private final String limitType;
    private final Integer limit;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null, null, null);
    }

    private ApiException(HttpStatus status, String code, String message,
            Integer remainingAttempts, Long retryAfterSeconds, String limitType, Integer limit) {
        super(message);
        this.status = status;
        this.code = code;
        this.remainingAttempts = remainingAttempts;
        this.retryAfterSeconds = retryAfterSeconds;
        this.limitType = limitType;
        this.limit = limit;
    }

    public static ApiException withRemainingAttempts(HttpStatus status, String code, String message, int remaining) {
        return new ApiException(status, code, message, remaining, null, null, null);
    }

    public static ApiException withRetryAfter(HttpStatus status, String code, String message, long seconds) {
        return new ApiException(status, code, message, null, seconds, null, null);
    }

    /** 개수 제한 초과 (BLG-10). 본문에 limitType(PUBLIC|PRIVATE)과 limit을 싣는다. */
    public static ApiException withLimit(HttpStatus status, String code, String message, String limitType,
            int limit) {
        return new ApiException(status, code, message, null, null, limitType, limit);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public ErrorResponse toResponse() {
        return new ErrorResponse(code, getMessage(), null, remainingAttempts, retryAfterSeconds, limitType, limit);
    }
}
