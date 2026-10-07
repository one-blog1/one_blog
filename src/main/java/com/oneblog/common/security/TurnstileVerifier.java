package com.oneblog.common.security;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 사람 확인 Cloudflare Turnstile (SEC-13, D-79). 로그인을 3번 틀린 뒤부터 요구한다.
 * TURNSTILE_SECRET_KEY가 없으면 꺼져 있다(개발·테스트). 운영에서는 Cloudflare에서 사이트를 등록해 키를 받아 넣는다.
 */
@Component
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String siteKey;
    private final String secretKey;
    private final RestClient client;

    public TurnstileVerifier(@Value("${app.turnstile.site-key:}") String siteKey,
            @Value("${app.turnstile.secret-key:}") String secretKey) {
        this.siteKey = siteKey == null ? "" : siteKey;
        this.secretKey = secretKey == null ? "" : secretKey;
        this.client = RestClient.create();
    }

    public boolean enabled() {
        return !secretKey.isBlank() && !siteKey.isBlank();
    }

    public String siteKey() {
        return enabled() ? siteKey : null;
    }

    /** 꺼져 있으면 true. 토큰이 없거나 Cloudflare가 거절하면 false. */
    public boolean verify(String token, String ip) {
        if (!enabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (ip != null) {
            form.add("remoteip", ip);
        }
        try {
            Map<?, ?> result = client.post().uri(VERIFY_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            return result != null && Boolean.TRUE.equals(result.get("success"));
        } catch (RuntimeException e) {
            log.warn("Turnstile 확인에 실패했습니다.", e);
            return false;
        }
    }
}
