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
    /** 썸네일이 영상 썸네일인 글: 글 ID → 사이트(youtube, chzzk, soop). 목록이 "▶ YouTube"처럼 표시한다 (D-128) */
    public final Map<Long, String> videoThumbnails = new HashMap<>();
    /** 본문 앞부분 글자 (목록 2줄 미리보기, D-128) */
    public final Map<Long, String> excerpts = new HashMap<>();
    /** 작성자 프로필 사진 (/files/...). 없거나 탈퇴·떠난 작성자는 비움 (D-128) */
    public final Map<Long, String> authorImages = new HashMap<>();

    public PostListContext(List<Post> posts) {
        this.posts = posts;
    }

    public List<Long> ids() {
        return posts.stream().map(Post::getId).toList();
    }
}
