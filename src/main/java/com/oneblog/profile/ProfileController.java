package com.oneblog.profile;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.oneblog.common.security.AuthenticatedUser;

/** 프로필 화면 /users/{닉네임}과 API (SOC-03). 닉네임이 프로필 주소다 (6.5). */
@Controller
public class ProfileController {

    private final ProfileService profileService;
    private final PrivacyService privacyService;

    public ProfileController(ProfileService profileService, PrivacyService privacyService) {
        this.profileService = profileService;
        this.privacyService = privacyService;
    }

    /** 설정 > 프로필 공개 범위 (D-114). */
    @GetMapping("/api/me/privacy")
    @ResponseBody
    public PrivacyService.Privacy privacy(@AuthenticationPrincipal AuthenticatedUser principal) {
        return privacyService.get(principal);
    }

    @PutMapping("/api/me/privacy")
    @ResponseBody
    public PrivacyService.Privacy updatePrivacy(@AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody PrivacyService.Privacy request) {
        return privacyService.update(principal, request);
    }

    @GetMapping("/users/{nickname}")
    public String page(@PathVariable("nickname") String nickname) {
        return "forward:/profile.html";
    }

    @GetMapping("/api/users/{nickname}")
    @ResponseBody
    public ProfileService.ProfileResponse profile(@PathVariable("nickname") String nickname,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return profileService.profile(nickname, principal == null ? null : principal.id());
    }
}
