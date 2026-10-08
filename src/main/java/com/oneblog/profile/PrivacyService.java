package com.oneblog.profile;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.account.AccountService;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.social.FollowGate;

/**
 * 프로필 공개 범위 (SOC-03, D-114): 프로필에 블로그 목록 보이기, 팔로워·팔로잉 목록 보이기, 팔로우 받기.
 * 본인에게는 늘 보이고, 끈 항목은 다른 사람에게만 숨긴다. 서버가 응답에서 빼고 팔로우를 막는다.
 */
@Service
public class PrivacyService implements FollowGate {

    private final UserRepository userRepository;
    private final AccountService accountService;

    public PrivacyService(UserRepository userRepository, AccountService accountService) {
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    public record Privacy(Boolean showBlogs, Boolean showFollows, Boolean allowFollow) {
    }

    @Transactional(readOnly = true)
    public Privacy get(AuthenticatedUser principal) {
        User user = accountService.member(principal);
        return new Privacy(user.isShowBlogsOnProfile(), user.isShowFollowsOnProfile(), user.isAllowFollow());
    }

    /** 보내지 않은 항목은 지금 값 그대로. */
    @Transactional
    public Privacy update(AuthenticatedUser principal, Privacy request) {
        User user = accountService.member(principal);
        Privacy r = request == null ? new Privacy(null, null, null) : request;
        user.changePrivacy(r.showBlogs() == null ? user.isShowBlogsOnProfile() : r.showBlogs(),
                r.showFollows() == null ? user.isShowFollowsOnProfile() : r.showFollows(),
                r.allowFollow() == null ? user.isAllowFollow() : r.allowFollow());
        userRepository.saveAndFlush(user);
        return get(principal);
    }

    /** 팔로우를 받지 않는 회원은 새로 팔로우할 수 없다. 이미 한 팔로우를 끊는 것은 막지 않는다. */
    @Override
    public void check(Long followerId, Long followeeId) {
        userRepository.findById(followeeId).filter(u -> !u.isAllowFollow()).ifPresent(u -> {
            throw new ApiException(HttpStatus.FORBIDDEN, "FOLLOW_NOT_ALLOWED", "이 회원은 팔로우를 받지 않아요.");
        });
    }

    /** 다른 사람의 팔로워·팔로잉 목록을 볼 수 있는지 (본인이면 늘). */
    @Transactional(readOnly = true)
    public void requireFollowsVisible(User target, Long viewerId) {
        if (!target.isShowFollowsOnProfile() && !target.getId().equals(viewerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FOLLOWS_PRIVATE", "이 회원은 팔로워 목록을 공개하지 않아요.");
        }
    }

}
