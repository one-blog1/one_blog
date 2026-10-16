package com.oneblog.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.oneblog.common.text.Masking;

/** 개인정보 가리기 형식 (4.4). */
class MaskingTest {

    @Test
    void 이메일은_앞_두_글자만() {
        assertThat(Masking.email("abcdef@gmail.com")).isEqualTo("ab***@gmail.com");
        assertThat(Masking.email("ab@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(Masking.email("a@gmail.com")).isEqualTo("a***@gmail.com");
        assertThat(Masking.email(null)).isNull();
    }

    @Test
    void 전화번호는_가운데를_가린다() {
        assertThat(Masking.phone("01012345678")).isEqualTo("010-****-5678");
        assertThat(Masking.phone("0111234567")).isEqualTo("011-***-4567");
        assertThat(Masking.formatPhone("01012345678")).isEqualTo("010-1234-5678");
    }

    @Test
    void 이름은_첫_글자와_끝_글자만_남긴다() {
        // 4.4 이름 (D-110): 처음과 끝만, 두 글자면 끝을 가림 (029 US4)
        assertThat(Masking.name("홍길동")).isEqualTo("홍*동");
        assertThat(Masking.name("김철")).isEqualTo("김*");
        assertThat(Masking.name("남궁민수")).isEqualTo("남**수");
        assertThat(Masking.name("이")).isEqualTo("*");
        assertThat(Masking.name("Alice")).isEqualTo("A***e");
        assertThat(Masking.name(" 홍길동 ")).isEqualTo("홍*동");
        assertThat(Masking.name(null)).isNull();
    }
}
