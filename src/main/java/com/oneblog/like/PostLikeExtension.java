package com.oneblog.like;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.Post;
import com.oneblog.post.PostExtension;
import com.oneblog.post.PostView;

/** 글 상세에 "내가 좋아요를 눌렀는지"를 채운다. */
@Component
public class PostLikeExtension implements PostExtension {

    private final PostLikeRepository likeRepository;

    public PostLikeExtension(PostLikeRepository likeRepository) {
        this.likeRepository = likeRepository;
    }

    @Override
    public void describe(Blog blog, Post post, AuthenticatedUser principal, PostView view) {
        view.liked = principal != null && likeRepository.existsByPostIdAndUserId(post.getId(), principal.id());
    }
}
