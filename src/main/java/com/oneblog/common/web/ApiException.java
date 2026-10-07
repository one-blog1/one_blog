package com.oneblog.common.web;

import org.springframework.http.HttpStatus;

/** 정해진 HTTP 상태와 오류 코드로 응답할 업무 예외. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Integer remainingAttempts;
    private final Long retryAfterSeconds;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, null, null);
    }

    private ApiException(HttpStatus status, String code, String message,
            Integer remainingAttempts, Long retryAfterSeconds) {
        super(message);
        this.status = status;
        this.code = code;
        this.remainingAttempts = remainingAttempts;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException withRemainingAttempts(HttpStatus status, String code, String message, int remaining) {
        return new ApiException(status, code, message, remaining, null);
    }

    public static ApiException withRetryAfter(HttpStatus status, String code, String message, long seconds) {
        return new ApiException(status, code, message, null, seconds);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public ErrorResponse toResponse() {
        return new ErrorResponse(code, getMessage(), null, remainingAttempts, retryAfterSeconds);
    }
}
