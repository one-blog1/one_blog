package com.oneblog.block;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogMemberRepository;
import com.oneblog.blog.join.JoinGate;

/** 블로그장이 차단한 회원의 참여 신청은 자동으로 거절된다 (SOC-05, D-36). */
@Component
public class BlockJoinGate implements JoinGate {

    private final BlockRepository blockRepository;
    private final BlogMemberRepository memberRepository;

    public BlockJoinGate(BlockRepository blockRepository, BlogMemberRepository memberRepository) {
        this.blockRepository = blockRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public Decision check(Blog blog, Long applicantId) {
        Long ownerId = memberRepository.findOwnerId(blog.getId());
        return blockRepository.exists(ownerId, applicantId) ? Decision.AUTO_REJECT : Decision.ALLOW;
    }
}
