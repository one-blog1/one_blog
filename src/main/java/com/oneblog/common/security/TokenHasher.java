package com.oneblog.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.oneblog.common.config.AppProperties;

/**
 * 토큰·인증번호를 원문 대신 해시로 저장하기 위한 도우미 (constitution III, 민감 정보 저장 규칙).
 */
@Component
public class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec pepperKey;

    public TokenHasher(AppProperties properties) {
        this.pepperKey = new SecretKeySpec(
                properties.verification().codePepper().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Refresh Token 저장용 SHA-256 (16진수 64자). */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 인증번호 저장용 HMAC-SHA256 (16진수 64자). 6자리 번호를 그대로 해시하면 쉽게 역산되므로 서버 비밀키를 섞는다. */
    public String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(pepperKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 길이가 같은 두 16진수 문자열을 상수 시간으로 비교한다. */
    public boolean matches(String expectedHex, String actualHex) {
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                actualHex.getBytes(StandardCharsets.US_ASCII));
    }

    /** 무작위 256비트 값 (URL에 안전한 Base64). */
    public String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 6자리 숫자 인증번호. */
    public String randomSixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
