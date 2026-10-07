package com.oneblog.member;

import java.util.List;

import com.oneblog.common.web.ErrorResponse;

/** 서버 규칙 검사(SignupPolicy) 실패. 400 VALIDATION_FAILED와 fieldErrors로 응답한다. */
public class ValidationFailedException extends RuntimeException {

    private final List<ErrorResponse.FieldError> fieldErrors;

    public ValidationFailedException(List<ErrorResponse.FieldError> fieldErrors) {
        super("입력값을 확인해 주세요.");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public List<ErrorResponse.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}
