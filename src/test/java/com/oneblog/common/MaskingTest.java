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
}
