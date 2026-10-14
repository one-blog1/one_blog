package com.oneblog.auth.social;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import com.oneblog.auth.AccessTokenService;

/**
 * 소셜 로그인 중간 상태를 서명한 쿠키 값(JWT)으로 만든다 (D-126). 서버 메모리·세션에 두지 않는다 (SCL-01).
 * - 가입 대기: 사이트에서 받은 회원 번호·이메일·이름·휴대전화·닉네임. 가입 화면이 이 값으로 가입을 마친다 (027)
 * - 연결 표시: 로그인한 회원이 내 정보에서 "연결하기"를 눌렀다는 표시. 돌아왔을 때 그 회원에 연결한다
 * typ이 ACCESS가 아니어서 로그인 쿠키로는 쓸 수 없다 (JwtCookieAuthenticationFilter).
 */
@Component
public class SocialTickets {

    public static final Duration PENDING_TTL = Duration.ofMinutes(15);
    public static final Duration LINK_TTL = Duration.ofMinutes(10);
    static final String TYPE_PENDING = "SOCIAL";
    static final String TYPE_LINK = "SOCIAL_LINK";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public SocialTickets(JwtEncoder encoder, JwtDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /**
     * 사이트에서 받은 정보. email은 없을 수 있고, emailVerified는 믿을 수 있는 이메일일 때만 true.
     * name·phone은 우리 형식에 맞는 값만 들어 있다(아니면 null). phone은 숫자만.
     */
    public record Pending(SocialProvider provider, String providerUserId, String email, boolean emailVerified,
            String name, String nickname, String phone) {
    }

    public String issuePending(Pending pending) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(PENDING_TTL))
                .claim(AccessTokenService.CLAIM_TYPE, TYPE_PENDING)
                .claim("provider", pending.provider().name())
                .claim("pid", pending.providerUserId())
                .claim("ev", pending.emailVerified());
        putIfText(claims, "email", pending.email());
        putIfText(claims, "name", pending.name());
        putIfText(claims, "nick", pending.nickname());
        putIfText(claims, "phone", pending.phone());
        return encode(claims.build());
    }

    public Optional<Pending> readPending(String token) {
        return decode(token, TYPE_PENDING).flatMap(jwt -> {
            Optional<SocialProvider> provider = SocialProvider.from(jwt.getClaimAsString("provider"));
            String pid = jwt.getClaimAsString("pid");
            if (provider.isEmpty() || pid == null || pid.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new Pending(provider.get(), pid, jwt.getClaimAsString("email"),
                    Boolean.TRUE.equals(jwt.getClaimAsBoolean("ev")), jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("nick"), jwt.getClaimAsString("phone")));
        });
    }

    public String issueLink(Long userId, SocialProvider provider) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(LINK_TTL))
                .claim(AccessTokenService.CLAIM_TYPE, TYPE_LINK)
                .claim("provider", provider.name())
                .build());
    }

    /** 연결 표시가 있으면 그 회원 ID를 돌려준다. 다른 사이트용 표시면 빈 값. */
    public Optional<Long> readLink(String token, SocialProvider provider) {
        return decode(token, TYPE_LINK)
                .filter(jwt -> provider.name().equals(jwt.getClaimAsString("provider")))
                .flatMap(jwt -> {
                    try {
                        return Optional.of(Long.valueOf(jwt.getSubject()));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    /** 서명·만료·용도가 맞는 JWT만 돌려준다. OAuth2 요청 쿠키(SocialAuthorizationRequestCookies)도 쓴다. */
    Optional<Jwt> decode(String token, String type) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            if (!type.equals(jwt.getClaimAsString(AccessTokenService.CLAIM_TYPE))) {
                return Optional.empty();
            }
            return Optional.of(jwt);
        } catch (JwtException e) {
            return Optional.empty();
        }
    }

    String encode(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static void putIfText(JwtClaimsSet.Builder claims, String name, String value) {
        if (value != null && !value.isBlank()) {
            claims.claim(name, value);
        }
    }
}
