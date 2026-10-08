package com.oneblog.post.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 글 상세. contentHtml은 서버가 거른 HTML이라 화면이 innerHTML로 넣어도 된다 (SEC-06).
 * content(마크다운 원문)는 고칠 수 있는 작성자에게만 준다.
 */
public record PostDetailResponse(
        Long id,
        String blogSlug,
        String blogName,
        String title,
        String contentHtml,
        @JsonInclude(JsonInclude.Include.NON_NULL) String content,
        Long authorId,
        String authorName,
        boolean notice,
        Long categoryId,
        String categoryName,
        List<String> tags,
        int viewCount,
        int likeCount,
        int commentCount,
        boolean liked,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean canEdit,
        boolean canDelete,
        boolean canComment,
        /** 관리자라서 보이는 글(숨긴 글, 숨김·비공개·일부 공개 블로그의 글)이면 그 이유 (D-106). */
        @JsonInclude(JsonInclude.Include.NON_NULL) String adminViewReason) {

    /** 이번 요청에서 센 조회수를 더한 응답 (014). */
    public PostDetailResponse withViewCount(int count) {
        return new PostDetailResponse(id, blogSlug, blogName, title, contentHtml, content, authorId, authorName, notice,
                categoryId, categoryName, tags, count, likeCount, commentCount, liked, createdAt, updatedAt, canEdit,
                canDelete, canComment, adminViewReason);
    }
}
