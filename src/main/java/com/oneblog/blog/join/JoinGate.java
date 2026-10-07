package com.oneblog.blog.join;

import com.oneblog.blog.Blog;

/**
 * 참여 신청 전 검사 (013). 블랙리스트에 걸리면 막고(예외), 블로그장이 차단한 회원은 자동 거절한다 (BLG-11, SOC-05).
 */
public interface JoinGate {

    enum Decision {
        ALLOW, AUTO_REJECT
    }

    /** 막을 때는 예외를 던진다. */
    Decision check(Blog blog, Long applicantId);
}
