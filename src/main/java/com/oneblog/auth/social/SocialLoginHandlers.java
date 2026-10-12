package com.oneblog.auth.social;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.oneblog.common.security.AuthCookies;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 카카오·네이버·구글에서 돌아왔을 때 (D-126). 서버 세션은 만들지 않고, 우리 로그인 쿠키를 주고 화면으로 보낸다.
 * - 연결된 회원: 로그인 쿠키 → 첫 화면
 * - 처음: 가입 대기 쿠키 → 소셜 가입 화면
 * - 내 정보에서 연결하기: 그 회원에 연결 → 내 정보 화면
 * 사이트에서 받은 토큰은 사용자 정보를 읽는 데만 쓰고 버린다.
 */
@Component
public class SocialLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(SocialLoginHandlers.class);

    private final SocialLoginService service;
    private final SocialTickets tickets;
    private final SocialCookies socialCookies;
    private final AuthCookies authCookies;

    public SocialLoginHandlers(SocialLoginService service, SocialTickets tickets, SocialCookies socialCookies,
            AuthCookies authCookies) {
        this.service = service;
        this.tickets = tickets;
        this.socialCookies = socialCookies;
        this.authCookies = authCookies;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        String linkCookie = SocialCookies.read(request, SocialCookies.LINK);
        if (linkCookie != null) {
            socialCookies.clearLink(response);
        }
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            response.sendRedirect("/login.html?social_error=failed");
            return;
        }
        Optional<SocialProvider> provider = SocialProvider.from(token.getAuthorizedClientRegistrationId());
        Optional<SocialTickets.Pending> profile = provider
                .flatMap(p -> SocialProfiles.from(p, token.getPrincipal().getAttributes()));
        if (profile.isEmpty()) {
            response.sendRedirect(linkCookie != null ? "/account.html?social_error=failed"
                    : "/login.html?social_error=failed");
            return;
        }

        Optional<Long> linkUserId = tickets.readLink(linkCookie, provider.get());
        if (linkUserId.isPresent()) {
            String error = service.onLink(linkUserId.get(), profile.get());
            response.sendRedirect(error == null ? "/account.html?social=linked"
                    : "/account.html?social_error=" + error);
            return;
        }

        SocialLoginService.CallbackResult result = service.onLogin(profile.get());
        if (result.login() != null) {
            authCookies.setLoginCookies(response, result.login().accessToken(), result.login().refreshToken(),
                    result.login().rememberMe());
            socialCookies.clearPending(response);
            response.sendRedirect("/");
        } else if (result.pending() != null) {
            socialCookies.setPending(response, tickets.issuePending(result.pending()));
            response.sendRedirect("/social-signup.html");
        } else {
            response.sendRedirect("/login.html?social_error=" + result.error());
        }
    }

    /** 사용자가 동의 화면에서 취소했거나, state가 맞지 않거나, 사이트 응답이 잘못된 경우. */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        String reason = "failed";
        if (exception instanceof OAuth2AuthenticationException oauth) {
            String code = oauth.getError().getErrorCode();
            if ("access_denied".equals(code)) {
                reason = "canceled";
            }
            log.info("소셜 로그인 실패: {}", code);
        }
        boolean linking = SocialCookies.read(request, SocialCookies.LINK) != null;
        if (linking) {
            socialCookies.clearLink(response);
        }
        response.sendRedirect((linking ? "/account.html" : "/login.html") + "?social_error=" + reason);
    }
}
