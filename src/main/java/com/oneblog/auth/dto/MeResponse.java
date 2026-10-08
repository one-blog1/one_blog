package com.oneblog.auth.dto;

/** 상단 메뉴용 내 정보. 프로필 사진이 없으면 profileImageUrl은 null (D-101). */
public record MeResponse(Long id, String nickname, String role, String profileImageUrl) {
}
