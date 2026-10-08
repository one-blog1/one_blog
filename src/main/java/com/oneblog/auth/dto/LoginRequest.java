package com.oneblog.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 회원 로그인 요청 본문: 이메일, 비밀번호, 로그인 유지, 사람 확인 토큰 (USR-03, SEC-13). */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해 주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
        Boolean rememberMe,
        /** 로그인을 3번 틀린 뒤 Turnstile이 준 토큰 (SEC-13) */
        String captchaToken) {

    public boolean rememberMeOrFalse() {
        return Boolean.TRUE.equals(rememberMe);
    }

    @Override
    public String toString() {
        return "LoginRequest[rememberMe=" + rememberMe + "]";
    }
}
