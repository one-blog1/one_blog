package com.oneblog.post.dto;

import java.util.List;

/** 여러 블로그 글 목록의 번호 페이지 (D-76). */
public record PostCardPage(
        List<PostCard> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
