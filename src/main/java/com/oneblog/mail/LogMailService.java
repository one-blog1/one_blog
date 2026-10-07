package com.oneblog.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 개발용: 메일을 보내지 않고 인증번호를 서버 로그에 남긴다 (research R6).
 * app.mail.mode=log일 때만 쓴다. 운영에서는 쓰지 않는다.
 */
@Service
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log")
public class LogMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(LogMailService.class);

    @Override
    public void sendSignupCode(String email, String code) {
        log.info("[메일 log 모드] 가입 인증번호 to={} code={}", email, code);
    }

    @Override
    public void sendAlreadyRegistered(String email) {
        log.info("[메일 log 모드] 이미 가입된 이메일 안내 to={}", email);
    }
}
