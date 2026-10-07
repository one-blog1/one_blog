package com.oneblog.member.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 가입 완료 요청 (USR-01 ②·③ 단계). 이메일은 본문으로 받지 않고 가입 티켓에서 읽는다.
 * 형식 규칙(SEC-02, 6.5)은 SignupPolicy가 검사한다.
 */
public record SignupRequest(
        @NotBlank(message = "비밀번호를 입력해 주세요.") String password,
        @NotBlank(message = "비밀번호를 한 번 더 입력해 주세요.") String passwordConfirm,
        @NotBlank(message = "이름을 입력해 주세요.") String name,
        @NotBlank(message = "닉네임을 입력해 주세요.") String nickname,
        @NotBlank(message = "전화번호를 입력해 주세요.") String phone,
        @NotNull @AssertTrue(message = "이용약관에 동의해 주세요.") Boolean agreeTerms,
        @NotNull @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.") Boolean agreePrivacy) {

    @Override
    public String toString() {
        // 비밀번호가 로그에 남지 않게 한다
        return "SignupRequest[nickname=" + nickname + "]";
    }
}
