package com.oneblog.mail;

/**
 * 인증 관련 메일 발송 (USR-02, D-28). 발송은 비동기라 응답 시간으로 가입 여부를 알 수 없다.
 * 발송 실패는 로그만 남기고 요청 응답에 영향을 주지 않는다.
 */
public interface MailService {

    /** 가입 인증번호 메일. */
    void sendSignupCode(String email, String code);

    /** 이미 가입된 이메일로 가입을 시도했을 때 인증번호 대신 보내는 안내 메일. */
    void sendAlreadyRegistered(String email);

    /** 비밀번호 재설정 인증번호 메일 (USR-06, 30분). */
    void sendPasswordResetCode(String email, String code);
}
