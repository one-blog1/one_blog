package com.oneblog.common.config;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app.* 설정. 비밀값은 환경변수(.env)로만 들어온다 (constitution III).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Verification verification, Mail mail, Cookie cookie) {

    public AppProperties {
        if (jwt == null || jwt.secret() == null
                || jwt.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret(JWT_SECRET)는 32바이트 이상이어야 합니다.");
        }
        if (verification == null || verification.codePepper() == null || verification.codePepper().isBlank()) {
            throw new IllegalStateException("app.verification.code-pepper(CODE_PEPPER)가 비어 있습니다.");
        }
        if (mail == null) {
            mail = new Mail(MailMode.SMTP);
        }
        if (cookie == null) {
            cookie = new Cookie(true);
        }
    }

    public record Jwt(String secret) {
    }

    public record Verification(String codePepper) {
    }

    public record Mail(MailMode mode) {
    }

    public record Cookie(boolean secure) {
    }

    public enum MailMode {
        SMTP, LOG
    }
}
