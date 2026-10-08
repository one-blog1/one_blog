package com.oneblog.common.security;

/** 로그인·가입·비밀번호 재확인 쿠키 이름을 한곳에 모은 상수 (SEC-04, D-102). */
public final class CookieNames {

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    public static final String REFRESH_TOKEN = "REFRESH_TOKEN";
    public static final String SIGNUP_TICKET = "SIGNUP_TICKET";
    public static final String REAUTH_TICKET = "REAUTH_TICKET";

    private CookieNames() {
    }
}
