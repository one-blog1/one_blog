package com.oneblog.block;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.oneblog.member.HiddenAuthors;

/** 내가 차단한 회원 = 가릴 작성자 (SOC-05). */
@Component
public class BlockHiddenAuthors implements HiddenAuthors {

    private final BlockRepository blockRepository;

    public BlockHiddenAuthors(BlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Override
    public Set<Long> of(Long viewerId) {
        return new HashSet<>(blockRepository.blockedIds(viewerId));
    }
}
