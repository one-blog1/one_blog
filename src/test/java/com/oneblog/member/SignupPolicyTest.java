package com.oneblog.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 비밀번호 규칙의 경계값: 8~15자, 영문·숫자·특수문자 모두 포함, 공백 불가 (SEC-02, 6.7, 029 US3). */
class SignupPolicyTest {

    private final SignupPolicy policy = new SignupPolicy();

    @Test
    void 여덟_자와_열다섯_자는_통과한다() {
        assertThat(policy.isValidPassword("Abcd12!x")).isTrue();
        assertThat(policy.isValidPassword("Abcd123!Abcd123")).isTrue();
        assertThat(policy.isValidPassword("abcd123!")).isTrue();
    }

    @Test
    void 일곱_자와_열여섯_자는_거부한다() {
        assertThat(policy.isValidPassword("Abc12!x")).isFalse();
        assertThat(policy.isValidPassword("Abcd123!Abcd1234")).isFalse();
    }

    @Test
    void 영문_숫자_특수문자_중_하나라도_빠지면_거부한다() {
        assertThat(policy.isValidPassword("Abcdefg!")).as("숫자 없음").isFalse();
        assertThat(policy.isValidPassword("1234567!")).as("영문 없음").isFalse();
        assertThat(policy.isValidPassword("Abcd1234")).as("특수문자 없음").isFalse();
    }

    @Test
    void 공백이나_빈_값은_거부한다() {
        assertThat(policy.isValidPassword("Abcd 12!")).isFalse();
        assertThat(policy.isValidPassword(" Abcd12!x")).isFalse();
        assertThat(policy.isValidPassword("")).isFalse();
        assertThat(policy.isValidPassword(null)).isFalse();
    }
}
