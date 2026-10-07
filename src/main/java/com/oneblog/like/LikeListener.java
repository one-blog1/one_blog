package com.oneblog.like;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.Post;

/** 좋아요를 누른 뒤 처리(알림, 011)가 끼어드는 자리. */
public interface LikeListener {

    void afterLike(Blog blog, Post post, AuthenticatedUser principal);
}
