package com.oneblog.post.dto;

import java.util.List;

/** 블로그 글 목록. 공지는 위에 따로 (최근 5개), 일반 글은 번호 페이지 (D-76). */
public record PostPageResponse(
        List<PostListItem> notices,
        List<PostListItem> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
