package com.oneblog.blog.dto;

import java.util.List;

/** 번호 페이지 목록 (D-76). page는 1부터. */
public record BlogPageResponse(
        List<BlogListItem> items,
        String sort,
        int page,
        int size,
        long totalItems,
        int totalPages) {
}
