package com.oneblog.post.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 글 목록의 한 줄 (BRD-02). 썸네일은 글의 첫 이미지 주소(005), 없으면 null. */
public record PostListItem(
        Long id,
        String title,
        String authorName,
        boolean notice,
        String categoryName,
        List<String> tags,
        String thumbnailUrl,
        int viewCount,
        int likeCount,
        int commentCount,
        OffsetDateTime createdAt) {
}
