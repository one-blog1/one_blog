package com.oneblog.auth.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** 사이트마다 다른 사용자 정보 모양 (D-126). 이메일은 사이트가 인증했다고 알려 준 경우만 믿는다. */
class SocialProfilesTest {

    @Test
    void 네이버는_response_안의_값을_쓰고_이메일은_인증되지_않은_것으로_본다() {
        SocialTickets.Pending p = SocialProfiles.from(SocialProvider.NAVER, Map.of("resultcode", "00",
                "response", Map.of("id", "naver-abc", "email", "a@naver.com", "name", "김네이버",
                        "nickname", "네이버친구"))).orElseThrow();
        assertThat(p.providerUserId()).isEqualTo("naver-abc");
        assertThat(p.email()).isEqualTo("a@naver.com");
        assertThat(p.emailVerified()).isFalse();
        assertThat(p.name()).isEqualTo("김네이버");
        assertThat(p.nickname()).isEqualTo("네이버친구");
    }

    @Test
    void 카카오는_인증_유효_표시가_모두_있어야_이메일을_믿는다() {
        SocialTickets.Pending verified = SocialProfiles.from(SocialProvider.KAKAO, Map.of("id", 12345L,
                "kakao_account", Map.of("email", "k@kakao.com", "is_email_valid", true, "is_email_verified", true,
                        "profile", Map.of("nickname", "카톡")))).orElseThrow();
        assertThat(verified.providerUserId()).isEqualTo("12345");
        assertThat(verified.emailVerified()).isTrue();
        assertThat(verified.nickname()).isEqualTo("카톡");

        SocialTickets.Pending unverified = SocialProfiles.from(SocialProvider.KAKAO, Map.of("id", 1L,
                "kakao_account", Map.of("email", "k@kakao.com", "is_email_verified", false))).orElseThrow();
        assertThat(unverified.emailVerified()).isFalse();
    }

    @Test
    void 구글은_email_verified가_참일_때만_믿는다() {
        assertThat(SocialProfiles.from(SocialProvider.GOOGLE, Map.of("sub", "g1", "email", "a@gmail.com",
                "email_verified", true)).orElseThrow().emailVerified()).isTrue();
        assertThat(SocialProfiles.from(SocialProvider.GOOGLE, Map.of("sub", "g1", "email", "a@gmail.com",
                "email_verified", false)).orElseThrow().emailVerified()).isFalse();
    }

    @Test
    void 회원_번호가_없으면_쓰지_않는다() {
        assertThat(SocialProfiles.from(SocialProvider.GOOGLE, Map.of("email", "a@gmail.com"))).isEmpty();
        assertThat(SocialProfiles.from(SocialProvider.NAVER, Map.of("resultcode", "024"))).isEmpty();
        assertThat(SocialProfiles.from(SocialProvider.KAKAO, Map.of())).isEmpty();
    }
}
