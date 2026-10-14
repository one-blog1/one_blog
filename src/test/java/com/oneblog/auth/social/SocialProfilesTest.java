package com.oneblog.auth.social;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** 사이트마다 다른 사용자 정보 모양 (D-126, 027). 카카오·구글 이메일은 인증 표시가 있을 때만, 네이버 이메일은 그대로 믿는다 (D-139). */
class SocialProfilesTest {

    @Test
    void 네이버는_response_안의_값을_쓰고_이메일을_믿는다() {
        SocialTickets.Pending p = SocialProfiles.from(SocialProvider.NAVER, Map.of("resultcode", "00",
                "response", Map.of("id", "naver-abc", "email", "a@naver.com", "name", "김네이버",
                        "nickname", "네이버친구", "mobile", "010-1234-5678"))).orElseThrow();
        assertThat(p.providerUserId()).isEqualTo("naver-abc");
        assertThat(p.email()).isEqualTo("a@naver.com");
        assertThat(p.emailVerified()).isTrue();
        assertThat(p.name()).isEqualTo("김네이버");
        assertThat(p.nickname()).isEqualTo("네이버친구");
        assertThat(p.phone()).isEqualTo("01012345678");
    }

    @Test
    void 네이버_이메일이_없으면_믿을_이메일도_없다() {
        SocialTickets.Pending p = SocialProfiles.from(SocialProvider.NAVER,
                Map.of("response", Map.of("id", "n1"))).orElseThrow();
        assertThat(p.email()).isNull();
        assertThat(p.emailVerified()).isFalse();
        assertThat(p.phone()).isNull();
    }

    @Test
    void 휴대전화는_숫자만_남기고_국가번호를_바꾸며_형식이_틀리면_버린다() {
        assertThat(SocialProfiles.phone("010-1234-5678")).isEqualTo("01012345678");
        assertThat(SocialProfiles.phone("+82 10-1234-5678")).isEqualTo("01012345678");
        assertThat(SocialProfiles.phone("+82 010-1234-5678")).isEqualTo("01012345678");
        assertThat(SocialProfiles.phone("011-123-4567")).isEqualTo("0111234567");
        assertThat(SocialProfiles.phone("1234")).isNull();
        assertThat(SocialProfiles.phone("02-123-4567")).isNull();
        assertThat(SocialProfiles.phone(null)).isNull();
    }

    @Test
    void 이름은_50자까지만_쓴다() {
        assertThat(SocialProfiles.name(" 홍길동 ")).isEqualTo("홍길동");
        assertThat(SocialProfiles.name("가".repeat(51))).isNull();
        assertThat(SocialProfiles.name("")).isNull();
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
    void 구글은_name을_이름으로_쓰고_휴대전화는_없다() {
        SocialTickets.Pending p = SocialProfiles.from(SocialProvider.GOOGLE, Map.of("sub", "g1",
                "email", "a@gmail.com", "email_verified", true, "name", "홍길동", "given_name", "길동"))
                .orElseThrow();
        assertThat(p.name()).isEqualTo("홍길동");
        assertThat(p.nickname()).isEqualTo("길동");
        assertThat(p.phone()).isNull();
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
