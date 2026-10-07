package com.oneblog.blog.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** 메인 목록의 블로그 하나 (BLG-02). */
public record BlogListItem(
        String slug,
        String name,
        String description,
        String coverImageUrl,
        List<String> tags,
        int memberCount,
        String ownerNickname,
        OffsetDateTime createdAt) {
}
