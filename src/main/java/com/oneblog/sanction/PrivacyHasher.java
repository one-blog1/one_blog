package com.oneblog.sanction;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 블랙리스트의 이름·이메일·전화번호는 원문 대신 해시로 둔다 (BLG-11, 4.6). 전화번호처럼 경우의 수가 적은 값도
 * 되돌릴 수 없게 서버 비밀값(pepper)을 섞은 HMAC-SHA256을 쓴다. 비교 전에 값을 정리한다(공백·대소문자·하이픈).
 * pepper를 바꾸면 기존 블랙리스트와 맞지 않으니 운영 중에는 바꾸지 않는다.
 */
@Component
public class PrivacyHasher {

    private final byte[] pepper;

    public PrivacyHasher(@Value("${app.privacy.hash-pepper:${app.verification.code-pepper}}") String pepper) {
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("app.privacy.hash-pepper(PRIVACY_HASH_PEPPER)가 비어 있습니다.");
        }
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    public String name(String name) {
        return hmac("name:" + (name == null ? "" : name.strip()));
    }

    public String email(String email) {
        return hmac("email:" + (email == null ? "" : email.strip().toLowerCase(Locale.ROOT)));
    }

    public String phone(String phone) {
        return hmac("phone:" + (phone == null ? "" : phone.replaceAll("[^0-9]", "")));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
