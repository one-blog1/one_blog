package com.oneblog.comment;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.Post;

/** 댓글을 쓰기 전 검사(차단·정지, 013)와 쓴 뒤 처리(알림, 011)가 끼어드는 자리. */
public interface CommentListener {

    default void beforeWrite(Blog blog, Post post, AuthenticatedUser principal) {
    }

    /** target은 답글을 단 댓글 (첫 댓글이면 null). */
    default void afterCreate(Blog blog, Post post, Comment comment, Comment target, AuthenticatedUser principal) {
    }
}
