package com.oneblog.admin;

import com.oneblog.comment.Comment;
import com.oneblog.post.Post;

/** 관리자 조치 뒤 처리(작성자 알림, 011)가 끼어드는 자리. */
public interface AdminListener {

    default void postDeletedByAdmin(Post post, String reason) {
    }

    default void commentDeletedByAdmin(Comment comment, String reason) {
    }
}
