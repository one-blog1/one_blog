package com.oneblog.auth.social;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 카카오·네이버·구글의 액세스 토큰을 저장하지 않는다 (D-126). 로그인할 때 사용자 정보를 한 번 읽고 끝이다.
 * 기본 저장소는 서버 메모리에 토큰을 쌓아 두므로(SCL-01 위반) 아무것도 하지 않는 저장소로 바꾼다.
 */
public final class NoTokenStorage implements OAuth2AuthorizedClientRepository {

    @Override
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
            Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // 저장하지 않는다
    }

    @Override
    public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
            HttpServletRequest request, HttpServletResponse response) {
        // 저장한 것이 없다
    }
}
