package com.oneblog.account;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 내 정보 보기·수정 (USR-07). */
@RestController
public class AccountController {

    private final AccountService accountService;
    private final WithdrawalService withdrawalService;
    private final com.oneblog.common.security.AuthCookies authCookies;

    public AccountController(AccountService accountService, WithdrawalService withdrawalService,
            com.oneblog.common.security.AuthCookies authCookies) {
        this.accountService = accountService;
        this.withdrawalService = withdrawalService;
        this.authCookies = authCookies;
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
            @RequestBody AccountService.ProfileUpdateRequest request) {
        return accountService.updateProfile(principal, request);
    }

    @PutMapping("/api/me/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody AccountService.PasswordChangeRequest request) {
        accountService.changePassword(principal, request);
        return ResponseEntity.noContent().build();
    }
}
