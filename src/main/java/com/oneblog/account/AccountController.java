package com.oneblog.account;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.security.CookieNames;

/** 내 정보 보기·수정 (USR-07). 수정은 비밀번호 재확인 뒤에만 (D-102). */
@RestController
public class AccountController {

    private final AccountService accountService;
    private final WithdrawalService withdrawalService;
    private final com.oneblog.common.security.AuthCookies authCookies;
    private final ReauthService reauthService;

    public AccountController(AccountService accountService, WithdrawalService withdrawalService,
            com.oneblog.common.security.AuthCookies authCookies, ReauthService reauthService) {
        this.accountService = accountService;
        this.withdrawalService = withdrawalService;
        this.authCookies = authCookies;
        this.reauthService = reauthService;
    }

    public record ReauthRequest(String password) {

        @Override
        public String toString() {
            return "ReauthRequest[***]";
        }
    }

    /** verified=false면 expiresAt은 null. */
    public record ReauthStatus(boolean verified, java.time.Instant expiresAt) {
    }

    /** 프로필 수정 전 비밀번호 재확인 (USR-07, D-102). */
    @org.springframework.web.bind.annotation.PostMapping("/api/me/reauth")
    public ReauthStatus reauth(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody ReauthRequest request, jakarta.servlet.http.HttpServletResponse response) {
        String ticket = reauthService.verify(principal, request.password());
        authCookies.setReauthTicket(response, ticket);
        return new ReauthStatus(true, reauthService.expiresAt(principal, ticket).orElse(null));
    }

    @GetMapping("/api/me/reauth")
    public ReauthStatus reauthStatus(@AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = CookieNames.REAUTH_TICKET, required = false) String ticket) {
        java.util.Optional<java.time.Instant> expiresAt = reauthService.expiresAt(principal, ticket);
        return new ReauthStatus(expiresAt.isPresent(), expiresAt.orElse(null));
    }

    public record WithdrawRequest(String password) {

        @Override
        public String toString() {
            return "WithdrawRequest[***]";
        }
    }

    /** 탈퇴 전에 정리해야 할 블로그 (USR-05). */
    @GetMapping("/api/me/withdrawal")
    public java.util.List<WithdrawalService.OwnedBlog> withdrawalCheck(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return withdrawalService.blockingBlogs(principal.id());
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/me/withdrawal")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody WithdrawRequest request, jakarta.servlet.http.HttpServletResponse response) {
        withdrawalService.withdraw(principal, request.password());
        authCookies.clearLoginCookies(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/account")
    public AccountService.AccountResponse account(@AuthenticationPrincipal AuthenticatedUser principal) {
        return accountService.account(principal);
    }

    @PutMapping("/api/me/profile")
    public AccountService.AccountResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = CookieNames.REAUTH_TICKET, required = false) String ticket,
            @RequestBody AccountService.ProfileUpdateRequest request) {
        reauthService.require(principal, ticket);
        return accountService.updateProfile(principal, request);
    }

    @PutMapping("/api/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @CookieValue(name = CookieNames.REAUTH_TICKET, required = false) String ticket,
            @RequestBody AccountService.PasswordChangeRequest request) {
        reauthService.require(principal, ticket);
        accountService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
