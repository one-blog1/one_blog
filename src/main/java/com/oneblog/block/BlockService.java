package com.oneblog.block;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.member.User;
import com.oneblog.member.UserRole;
import com.oneblog.social.FollowRepository;
import com.oneblog.social.FollowService;

/**
 * 차단 (SOC-05, D-36). 차단한 회원의 글·댓글을 숨기고 서로의 팔로우를 끊는다.
 * 같은 블로그의 멤버끼리는 차단해도 그 블로그 안의 글이 보인다. 블로그장이 차단한 회원의 참여 신청은 자동 거절 (BlockJoinGate).
 */
@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowService followService;
    private final FollowRepository followRepository;
    private final NamedParameterJdbcTemplate jdbc;

    public BlockService(BlockRepository blockRepository, FollowService followService, FollowRepository followRepository,
            NamedParameterJdbcTemplate jdbc) {
        this.blockRepository = blockRepository;
        this.followService = followService;
        this.followRepository = followRepository;
        this.jdbc = jdbc;
    }

    public record BlockState(boolean blocked) {
    }

    public record BlockedUser(String nickname, String profileImageUrl) {
    }

    @Transactional
    public BlockState toggle(AuthenticatedUser principal, String nickname) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 차단을 쓰지 않습니다.");
        }
        User target = followService.findMember(nickname);
        if (target.getId().equals(principal.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_BLOCK_SELF", "자기 자신은 차단할 수 없습니다.");
        }
        if (blockRepository.delete(principal.id(), target.getId()) > 0) {
            return new BlockState(false);
        }
        blockRepository.insert(principal.id(), target.getId());
        followRepository.deleteBetween(principal.id(), target.getId());
        return new BlockState(true);
    }

    @Transactional(readOnly = true)
    public List<BlockedUser> myBlocks(Long userId) {
        return jdbc.query("""
                SELECT u.nickname, u.profile_image_url FROM blocks b JOIN users u ON u.id = b.blocked_user_id
                WHERE b.user_id = :userId AND u.status = 'ACTIVE' ORDER BY b.created_at DESC
                """, new MapSqlParameterSource("userId", userId),
                (rs, i) -> new BlockedUser(rs.getString(1), rs.getString(2)));
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(Long userId, Long targetId) {
        return blockRepository.exists(userId, targetId);
    }
}
