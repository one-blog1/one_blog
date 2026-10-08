package com.oneblog.verification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 인증번호 확인 요청 본문: 이메일과 6자리 인증번호 (USR-02). */
public record VerifyCodeRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일이 너무 깁니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리입니다.")
        String code) {
}
