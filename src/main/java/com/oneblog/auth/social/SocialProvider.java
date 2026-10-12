package com.oneblog.auth.social;

import java.util.Locale;
import java.util.Optional;

/** 소셜 로그인 사이트 (USR-09, D-126). DB에는 이름(KAKAO 등)을, 주소에는 소문자(kakao 등)를 쓴다. */
public enum SocialProvider {

    KAKAO("카카오"),
    NAVER("네이버"),
    GOOGLE("구글");

    private final String label;

    SocialProvider(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 로그인 주소(/oauth2/authorization/{id})와 돌아오는 주소(/login/oauth2/code/{id})에 쓰는 이름. */
    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SocialProvider> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (SocialProvider provider : values()) {
            if (provider.name().equalsIgnoreCase(value)) {
                return Optional.of(provider);
            }
        }
        return Optional.empty();
    }
}
