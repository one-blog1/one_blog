package com.oneblog.auth.dto;

/** 로그인 성공 응답: 화면 상단에 보여 줄 닉네임. */
public record LoginResponse(String nickname) {
}
