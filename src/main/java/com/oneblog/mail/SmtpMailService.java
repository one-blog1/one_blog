package com.oneblog.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.oneblog.common.config.AsyncConfig;

/** Gmail SMTP로 실제 메일을 보낸다 (D-69). app.mail.mode=smtp(기본)일 때 쓴다. */
@Service
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp", matchIfMissing = true)
public class SmtpMailService implements MailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailService.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpMailService(JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @Override
    public void sendSignupCode(String email, String code) {
        send(email, MailTexts.SIGNUP_CODE_SUBJECT, MailTexts.signupCode(code));
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @Override
    public void sendAlreadyRegistered(String email) {
        send(email, MailTexts.ALREADY_REGISTERED_SUBJECT, MailTexts.alreadyRegistered());
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            mailSender.send(message);
        } catch (MailException e) {
            // 받는 사람 주소는 로그에 남기지 않는다
            log.error("메일 발송 실패: {}", subject, e);
        }
    }
}
