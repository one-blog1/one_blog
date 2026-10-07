package com.oneblog.blog.join;

import com.oneblog.blog.Blog;

/** 참여 신청이 오거나 처리됐을 때 (알림 011: JOIN_REQUEST, JOIN_RESULT). */
public interface JoinListener {

    default void requested(Blog blog, Long applicantId) {
    }

    default void processed(Blog blog, Long applicantId, boolean approved) {
    }
}
