package com.oneblog.social;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.PageParams;
import com.oneblog.member.User;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;

/**
 * 팔로우·언팔로우와 팔로워·팔로잉 목록 (SOC-01, SOC-02, 6.6: 한 번만, 다시 누르면 취소).
 * 관리자는 팔로우하지 않는다 (D-90). 자기 자신은 팔로우할 수 없다 (ck_follows_self).
 */
@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final List<FollowListener> listeners;
    private final List<FollowGate> gates;

    public FollowService(FollowRepository followRepository, UserRepository userRepository,
            NamedParameterJdbcTemplate jdbc, List<FollowListener> listeners, List<FollowGate> gates) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.listeners = listeners;
        this.gates = gates;
    }

    public record FollowState(boolean following, long followerCount) {
    }

    public record UserItem(String nickname, String profileImageUrl, String bio) {
    }

    public record UserPage(List<UserItem> items, int page, int size, long totalItems, int totalPages) {
    }

    /** 누를 때마다 팔로우와 취소를 오간다. 동시에 두 번 눌러도 uk_follows가 한 줄만 남긴다. */
    @Transactional
    public FollowState toggle(AuthenticatedUser principal, String nickname) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 팔로우할 수 없습니다.");
        }
        User target = findMember(nickname);
        if (target.getId().equals(principal.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_FOLLOW_SELF", "자기 자신은 팔로우할 수 없습니다.");
        }
        // 같은 회원의 요청을 줄 세워 팔로우·취소가 엇갈리지 않게 한다
        userRepository.findForUpdateById(principal.id());
        var existing = followRepository.findByFollowerIdAndFolloweeId(principal.id(), target.getId());
        boolean following;
        if (existing.isPresent()) {
            followRepository.delete(existing.get());
            following = false;
        } else {
            gates.forEach(g -> g.check(principal.id(), target.getId()));
            try {
                followRepository.saveAndFlush(Follow.of(principal.id(), target.getId()));
            } catch (DataIntegrityViolationException e) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_FOLLOWING", "이미 팔로우했습니다.");
            }
            following = true;
            listeners.forEach(l -> l.followed(principal.id(), target.getId()));
        }
        followRepository.flush();
        return new FollowState(following, followRepository.countFollowers(target.getId()));
    }

    @Transactional(readOnly = true)
    public UserPage followers(String nickname, PageParams params) {
        User target = findMember(nickname);
        return page("f.followee_id = :userId", "u.id = f.follower_id", target.getId(), params);
    }

    @Transactional(readOnly = true)
    public UserPage following(String nickname, PageParams params) {
        User target = findMember(nickname);
        return page("f.follower_id = :userId", "u.id = f.followee_id", target.getId(), params);
    }

    /** 조건은 이 클래스 안의 고정 문자열만 쓰고, 값은 바인딩한다. */
    private UserPage page(String where, String join, Long userId, PageParams params) {
        String from = " FROM follows f JOIN users u ON " + join + " WHERE " + where + " AND u.status = 'ACTIVE'";
        MapSqlParameterSource args = new MapSqlParameterSource("userId", userId);
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + from, args, Long.class);
        long totalItems = total == null ? 0 : total;
        int totalPages = (int) Math.max(1, (totalItems + params.size() - 1) / params.size());
        if (params.page() > totalPages) {
            params = params.firstPage();
        }
        args.addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        List<UserItem> items = jdbc.query("SELECT u.nickname, u.profile_image_url, u.bio" + from
                + " ORDER BY f.created_at DESC, f.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, i) -> new UserItem(rs.getString(1), rs.getString(2), rs.getString(3)));
        return new UserPage(items, params.page(), params.size(), totalItems, totalPages);
    }

    /** 닉네임으로 찾은 활성 일반 회원. 탈퇴했거나 관리자면 없는 회원. */
    public User findMember(String nickname) {
        String value = nickname == null ? "" : nickname.strip();
        return userRepository.findByNickname(value)
                .filter(u -> u.isActive() && u.getRole() == UserRole.USER)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "회원을 찾을 수 없습니다."));
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followerId != null && followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }
}
