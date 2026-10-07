package com.oneblog.common.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.oneblog.member.ValidationFailedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_MESSAGE = "입력값을 확인해 주세요.";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(e.toResponse());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", VALIDATION_MESSAGE, fields, null, null));
    }

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<ErrorResponse> handleRuleValidation(ValidationFailedException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_FAILED", VALIDATION_MESSAGE, e.getFieldErrors(), null, null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("VALIDATION_FAILED", VALIDATION_MESSAGE));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_FAILED", VALIDATION_MESSAGE,
                List.of(new ErrorResponse.FieldError(e.getRequestPartName(), "파일을 골라 주세요.")), null, null));
    }

    /** 요청 전체가 업로드 상한(10MB)을 넘음. 한 파일 3MB 검사는 FileUploadService가 한다 (6.3). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(ErrorResponse.of("FILE_TOO_LARGE", "파일은 3MB까지 올릴 수 있습니다."));
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingCookie(MissingRequestCookieException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHENTICATED", "다시 시도해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 404(없는 주소), 405(잘못된 메서드) 같은 Spring MVC 표준 오류는 그 상태 그대로 돌려준다
        if (e instanceof org.springframework.web.ErrorResponse standard) {
            return ResponseEntity.status(standard.getStatusCode())
                    .body(ErrorResponse.of("REQUEST_FAILED", "요청을 처리할 수 없습니다."));
        }
        // 내부 메시지는 응답에 노출하지 않는다
        log.error("처리하지 못한 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "잠시 후 다시 시도해 주세요."));
    }
}
