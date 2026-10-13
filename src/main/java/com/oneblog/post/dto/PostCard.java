package com.oneblog.post.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 여러 블로그의 글이 섞인 목록(태그 008, 검색·피드 010)의 한 줄. 어느 블로그 글인지 함께 준다.
 * 썸네일·영상 썸네일·미리보기·작성자 사진은 PostListItem과 같다 (D-128).
 */
public record PostCard(
        Long id,
        String blogSlug,
        String blogName,
        String title,
        String authorName,
        String authorImageUrl,
        String categoryName,
        List<String> tags,
        String thumbnailUrl,
        String thumbnailVideo,
        String excerpt,
        int viewCount,
        int likeCount,
        int commentCount,
        OffsetDateTime createdAt) {
}
