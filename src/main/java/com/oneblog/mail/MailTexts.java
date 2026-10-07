package com.oneblog.mail;

/** 메일 제목과 본문 (한국어 평문). */
final class MailTexts {

    static final String SIGNUP_CODE_SUBJECT = "[One Blog] 회원가입 인증번호";
    static final String ALREADY_REGISTERED_SUBJECT = "[One Blog] 회원가입 안내";

    private MailTexts() {
    }

    static String signupCode(String code) {
        return """
                One Blog 회원가입 인증번호입니다.

                인증번호: %s

                10분 안에 가입 화면에 입력해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(code);
    }

    static String alreadyRegistered() {
        return """
                이 이메일로 One Blog 회원가입을 시도했지만, 이미 가입된 이메일입니다.

                로그인 화면에서 로그인해 주세요.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """;
    }
}
