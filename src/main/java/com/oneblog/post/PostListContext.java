package com.oneblog.post;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 목록에서 부가 기능이 글 ID별로 채우는 값. */
public class PostListContext {

    public final List<Post> posts;
    public final Map<Long, String> categoryNames = new HashMap<>();
    public final Map<Long, List<String>> tags = new HashMap<>();
    public final Map<Long, String> thumbnails = new HashMap<>();

    public PostListContext(List<Post> posts) {
        this.posts = posts;
    }

    public List<Long> ids() {
        return posts.stream().map(Post::getId).toList();
    }
}
