package com.oneblog.auth.social;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.stereotype.Component;

/**
 * 카카오·네이버·구글 앱 설정 (D-126). 키(클라이언트 ID·시크릿)가 들어온 사이트만 켠다.
 * 키는 환경변수(.env, GitHub Secrets)로만 넣는다 (constitution III).
 * 돌아오는 주소는 PUBLIC_BASE_URL이 있으면 그 주소로, 없으면 요청 주소로 만든다. 사이트 개발자 화면에 등록한 주소와 같아야 한다.
 */
@Component
public class SocialClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    private final Map<SocialProvider, ClientRegistration> registrations = new EnumMap<>(SocialProvider.class);

    public SocialClientRegistrations(
            @Value("${app.social.kakao.client-id:}") String kakaoId,
            @Value("${app.social.kakao.client-secret:}") String kakaoSecret,
            @Value("${app.social.naver.client-id:}") String naverId,
            @Value("${app.social.naver.client-secret:}") String naverSecret,
            @Value("${app.social.google.client-id:}") String googleId,
            @Value("${app.social.google.client-secret:}") String googleSecret,
            @Value("${app.public-base-url:}") String publicBaseUrl) {
        String base = publicBaseUrl == null || publicBaseUrl.isBlank()
                ? "{baseUrl}" : publicBaseUrl.strip().replaceAll("/+$", "");
        String redirect = base + "/login/oauth2/code/{registrationId}";
        if (has(kakaoId)) {
            // 비즈 앱이 아니면 카카오는 이메일을 주지 않는다. 닉네임만 받고 이메일은 가입 화면에서 인증받는다
            registrations.put(SocialProvider.KAKAO, builder(SocialProvider.KAKAO, kakaoId, kakaoSecret, redirect)
                    .clientName("카카오")
                    .scope("profile_nickname")
                    .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                    .tokenUri("https://kauth.kakao.com/oauth/token")
                    .userInfoUri("https://kapi.kakao.com/v2/user/me")
                    .userNameAttributeName("id")
                    .build());
        }
        if (has(naverId)) {
            // 네이버 사용자 정보는 response 안에 들어 있다. 네이버는 PKCE를 받지 않아 끈다
            registrations.put(SocialProvider.NAVER, builder(SocialProvider.NAVER, naverId, naverSecret, redirect)
                    .clientName("네이버")
                    .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                    .tokenUri("https://nid.naver.com/oauth2.0/token")
                    .userInfoUri("https://openapi.naver.com/v1/nid/me")
                    .userNameAttributeName("response")
                    .clientSettings(ClientRegistration.ClientSettings.builder().requireProofKey(false).build())
                    .build());
        }
        if (has(googleId)) {
            // openid를 빼고 OAuth2 사용자 정보 API로 받는다 (ID 토큰 검증 없이 sub·email·email_verified만 쓴다)
            registrations.put(SocialProvider.GOOGLE, builder(SocialProvider.GOOGLE, googleId, googleSecret, redirect)
                    .clientName("구글")
                    .scope("profile", "email")
                    .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token")
                    .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                    .userNameAttributeName("sub")
                    .build());
        }
    }

    private static ClientRegistration.Builder builder(SocialProvider provider, String clientId, String secret,
            String redirect) {
        // 시크릿을 쓰지 않는 앱(카카오에서 끌 수 있다)은 시크릿 없이 PKCE로만 토큰을 받는다
        boolean withSecret = has(secret);
        return ClientRegistration.withRegistrationId(provider.registrationId())
                .clientId(clientId.strip())
                .clientSecret(withSecret ? secret.strip() : null)
                .clientAuthenticationMethod(withSecret
                        ? ClientAuthenticationMethod.CLIENT_SECRET_POST : ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(redirect);
    }

    private static boolean has(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return SocialProvider.from(registrationId).map(registrations::get).orElse(null);
    }

    /** 키가 들어와 켜진 사이트 (로그인 화면 버튼). */
    public List<SocialProvider> enabled() {
        return List.copyOf(registrations.keySet());
    }

    public boolean isEnabled(SocialProvider provider) {
        return registrations.containsKey(provider);
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return Collections.unmodifiableCollection(registrations.values()).iterator();
    }
}
