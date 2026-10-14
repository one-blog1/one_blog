package com.oneblog.auth.social;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.auth.AuthService.LoginResult;
import com.oneblog.auth.dto.LoginResponse;
import com.oneblog.common.security.AuthCookies;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.security.CookieNames;
import com.oneblog.common.web.ApiException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 소셜 로그인 API (USR-09, D-126). 사이트로 보내고 받는 주소(/oauth2/**, /login/oauth2/**)는 Spring Security가 맡는다. */
@RestController
public class SocialController {

    private final SocialLoginService service;
    private final SocialTickets tickets;
    private final SocialCookies socialCookies;
    private final AuthCookies authCookies;
    private final SocialClientRegistrations registrations;

    public SocialController(SocialLoginService service, SocialTickets tickets, SocialCookies socialCookies,
            AuthCookies authCookies, SocialClientRegistrations registrations) {
        this.service = service;
        this.tickets = tickets;
        this.socialCookies = socialCookies;
        this.authCookies = authCookies;
        this.registrations = registrations;
    }

    public record ProviderItem(String provider, String label, String loginUrl) {
    }

    /** 로그인 화면에 보일 버튼. 키가 들어온 사이트만. */
    @GetMapping("/api/auth/social/providers")
    public List<ProviderItem> providers() {
        return registrations.enabled().stream()
                .map(p -> new ProviderItem(p.name(), p.label(), loginUrl(p)))
                .toList();
    }

    /** 소셜 가입 화면이 처음 읽는 값. 가입 대기 쿠키가 없거나 지났으면 401. */
    @GetMapping("/api/auth/social/pending")
    public SocialLoginService.PendingView pending(
            @CookieValue(name = SocialCookies.PENDING, required = false) String pending) {
        return service.view(readPending(pending));
    }

    /** 소셜 계정으로 가입하고 바로 로그인한다. 이메일 인증이 필요한 사이트는 가입 티켓(SIGNUP_TICKET)도 있어야 한다. */
    @PostMapping("/api/auth/signup/social")
    public ResponseEntity<LoginResponse> signup(
            @CookieValue(name = SocialCookies.PENDING, required = false) String pending,
            @CookieValue(name = CookieNames.SIGNUP_TICKET, required = false) String signupTicket,
            @RequestBody SocialLoginService.SocialSignupRequest request, HttpServletResponse response) {
        LoginResult result = service.signup(readPending(pending), signupTicket, request);
        loggedIn(response, result);
        authCookies.clearSignupTicket(response);
        return ResponseEntity.status(HttpStatus.CREATED).body(new LoginResponse(result.nickname()));
    }

    /** email은 비워도 된다. 비우면 믿을 수 있는 소셜 이메일을 쓴다 (027). */
    public record LinkRequest(String email, String password, String captchaToken) {

        @Override
        public String toString() {
            return "LinkRequest[email=" + SocialMasks.email(email) + "]";
        }
    }

    /** 이미 가입한 회원이 비밀번호로 로그인하면서 소셜 계정을 연결한다. */
    @PostMapping("/api/auth/social/link")
    public LoginResponse link(@CookieValue(name = SocialCookies.PENDING, required = false) String pending,
            @RequestBody LinkRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
        LoginResult result = service.linkWithPassword(readPending(pending), request.email(), request.password(),
                httpRequest.getRemoteAddr(), request.captchaToken());
        loggedIn(response, result);
        return new LoginResponse(result.nickname());
    }

    /** 가입 화면에서 그만두기. 가입 대기 쿠키를 지운다. */
    @PostMapping("/api/auth/social/cancel")
    public ResponseEntity<Void> cancel(HttpServletResponse response) {
        socialCookies.clearPending(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/social")
    public SocialLoginService.MySocial mine(@AuthenticationPrincipal AuthenticatedUser principal) {
        return service.mine(principal.id());
    }

    /**
     * 내 정보에서 연결하기. 연결 표시 쿠키를 주고, 화면은 돌려준 주소로 이동한다.
     * 표시 쿠키는 CSRF 검사를 거친 이 요청에서만 만들어지므로 다른 사이트가 남의 계정에 연결시킬 수 없다.
     */
    @PostMapping("/api/me/social/{provider}/link")
    public Map<String, String> startLink(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("provider") String provider, HttpServletResponse response) {
        SocialProvider p = provider(provider);
        service.checkCanStartLink(principal.id(), p);
        socialCookies.setLink(response, tickets.issueLink(principal.id(), p));
        return Map.of("url", loginUrl(p));
    }

    @DeleteMapping("/api/me/social/{provider}")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable("provider") String provider) {
        service.unlink(principal.id(), provider(provider));
        return ResponseEntity.noContent().build();
    }

    private void loggedIn(HttpServletResponse response, LoginResult result) {
        authCookies.setLoginCookies(response, result.accessToken(), result.refreshToken(), result.rememberMe());
        socialCookies.clearPending(response);
    }

    private SocialTickets.Pending readPending(String value) {
        return tickets.readPending(value).orElseThrow(SocialLoginService::pendingExpired);
    }

    private static SocialProvider provider(String value) {
        return SocialProvider.from(value).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_DISABLED", "지금은 쓸 수 없는 로그인 방법입니다."));
    }

    private static String loginUrl(SocialProvider provider) {
        return "/oauth2/authorization/" + provider.registrationId();
    }
}
