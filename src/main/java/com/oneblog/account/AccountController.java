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

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
