package com.oneblog.tag;

import java.util.List;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.Post;
import com.oneblog.post.PostExtension;
import com.oneblog.post.PostListContext;
import com.oneblog.post.PostView;
import com.oneblog.post.dto.PostRequest;

/** 글 태그 (BRD-04, 6.4): 저장 전 정리·검사, 저장 뒤 통째로 바꾸기, 상세·목록에 채우기. */
@Component
public class PostTagExtension implements PostExtension {

    private final TagPolicy tagPolicy;
    private final TagService tagService;

    public PostTagExtension(TagPolicy tagPolicy, TagService tagService) {
        this.tagPolicy = tagPolicy;
        this.tagService = tagService;
    }

    @Override
    public void validate(Blog blog, PostRequest request) {
        tagPolicy.normalizeAll(request.tags(), "tags");
    }

    @Override
    public void afterSave(Blog blog, Post post, PostRequest request, AuthenticatedUser principal) {
        tagService.replacePostTags(post.getId(), tagPolicy.normalizeAll(request.tags(), "tags"));
    }

    @Override
    public void describe(Blog blog, Post post, AuthenticatedUser principal, PostView view) {
        view.tags = tagService.findPostTags(List.of(post.getId())).getOrDefault(post.getId(), List.of());
    }

    @Override
    public void describeList(Blog blog, PostListContext context) {
        if (!context.posts.isEmpty()) {
            context.tags.putAll(tagService.findPostTags(context.ids()));
        }
    }
}
