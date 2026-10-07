package com.oneblog.common.security;

import com.oneblog.member.UserRole;

/**
 * 요청마다 DB로 확인한 로그인 사용자. 컨트롤러에서 @AuthenticationPrincipal로 받는다.
 */
public record AuthenticatedUser(Long id, Long sessionId, UserRole role) {
}
