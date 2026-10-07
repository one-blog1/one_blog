package com.oneblog.block;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oneblog.common.web.ApiException;
import com.oneblog.social.FollowGate;

/** 둘 중 한 사람이 상대를 차단했으면 팔로우할 수 없다 (SOC-05: 차단하면 서로의 팔로우를 끊는다). */
@Component
public class BlockFollowGate implements FollowGate {

    private final BlockRepository blockRepository;

    public BlockFollowGate(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Override
    public void check(Long followerId, Long followeeId) {
        if (blockRepository.eitherBlocked(followerId, followeeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "BLOCKED", "차단한 사이에서는 팔로우할 수 없습니다.");
        }
    }
}
