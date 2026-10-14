package com.oneblog.auth.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 소셜 가입 화면에 보여 줄 가린 값 (027, 4.4). */
class SocialMasksTest {

    @Test
    void 이름은_첫_글자와_끝_글자만_남긴다() {
        assertThat(SocialMasks.name("홍길동")).isEqualTo("홍*동");
        assertThat(SocialMasks.name("남궁민수")).isEqualTo("남**수");
        assertThat(SocialMasks.name("홍길")).isEqualTo("홍*");
        assertThat(SocialMasks.name("홍")).isEqualTo("홍");
        assertThat(SocialMasks.name(null)).isNull();
    }

    @Test
    void 휴대전화는_가운데를_가린다() {
        assertThat(SocialMasks.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(SocialMasks.phone("0111234567")).isEqualTo("011-***-4567");
        assertThat(SocialMasks.phone("1234")).isNull();
        assertThat(SocialMasks.phone(null)).isNull();
    }

    @Test
    void 이메일은_앞_두_글자와_도메인만_남긴다() {
        assertThat(SocialMasks.email("abcdef@naver.com")).isEqualTo("ab***@naver.com");
        assertThat(SocialMasks.email("ab@naver.com")).isEqualTo("a***@naver.com");
        assertThat(SocialMasks.email("a@naver.com")).isEqualTo("a***@naver.com");
        assertThat(SocialMasks.email("not-an-email")).isNull();
        assertThat(SocialMasks.email(null)).isNull();
    }
}
