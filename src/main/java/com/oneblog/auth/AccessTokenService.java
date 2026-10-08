package com.oneblog.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.oneblog.common.security.AuthCookies;

/**
 * Access Token과 가입 티켓(JWT)을 만들고 검증한다 (research R3, R5).
 * Access Token에는 회원 ID(sub)와 로그인 행 ID(sid)만 담고, 역할·상태는 요청마다 DB에서 확인한다 (constitution III).
 */
@Service
public class AccessTokenService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(10);
    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_SESSION_ID = "sid";
    public static final String TYPE_ACCESS = "ACCESS";
    public static final String TYPE_SIGNUP = "SIGNUP";
    public static final String TYPE_REAUTH = "REAUTH";
    /** 프로필 수정 전 비밀번호 재확인이 유효한 시간 (USR-07, D-102). */
    public static final Duration REAUTH_TTL = Duration.ofMinutes(10);
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_VERIFICATION_ID = "vid";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public AccessTokenService(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    public String issueAccessToken(Long userId, Long sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .claim(CLAIM_SESSION_ID, String.valueOf(sessionId))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .build();
        return encode(claims);
    }

    public String issueSignupTicket(String email, Long verificationId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(AuthCookies.SIGNUP_TICKET_TTL))
                .claim(CLAIM_TYPE, TYPE_SIGNUP)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_VERIFICATION_ID, String.valueOf(verificationId))
                .build();
        return encode(claims);
    }

    /** 비밀번호 재확인 티켓. 회원(sub)과 로그인 행(sid)에 묶여 다른 기기에서는 쓸 수 없다 (D-102). */
    public String issueReauthTicket(Long userId, Long sessionId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(REAUTH_TTL))
                .claim(CLAIM_SESSION_ID, String.valueOf(sessionId))
                .claim(CLAIM_TYPE, TYPE_REAUTH)
                .build();
        return encode(claims);
    }

    /** 이 회원·이 로그인의 재확인 티켓이면 만료 시각을, 아니면 빈 값을 돌려준다. */
    public Optional<Instant> readReauthTicket(String token, Long userId, Long sessionId) {
        if (token == null || token.isBlank() || userId == null || sessionId == null) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!TYPE_REAUTH.equals(jwt.getClaimAsString(CLAIM_TYPE))
                    || !String.valueOf(userId).equals(jwt.getSubject())
                    || !String.valueOf(sessionId).equals(jwt.getClaimAsString(CLAIM_SESSION_ID))
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(jwt.getExpiresAt());
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    /** 서명·만료·용도를 검사하고 가입 티켓 내용을 돌려준다. 하나라도 틀리면 비어 있다. */
    public Optional<SignupTicket> readSignupTicket(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!TYPE_SIGNUP.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
                return Optional.empty();
            }
            String email = jwt.getClaimAsString(CLAIM_EMAIL);
            String vid = jwt.getClaimAsString(CLAIM_VERIFICATION_ID);
            if (email == null || vid == null) {
                return Optional.empty();
            }
            return Optional.of(new SignupTicket(email, Long.valueOf(vid)));
        } catch (JwtException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record SignupTicket(String email, Long verificationId) {
        public SignupTicket {
            Objects.requireNonNull(email);
            Objects.requireNonNull(verificationId);
        }
    }
}
