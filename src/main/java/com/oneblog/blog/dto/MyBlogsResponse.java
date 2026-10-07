package com.oneblog.blog.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.oneblog.blog.BlogRole;
import com.oneblog.blog.BlogVisibility;

/** 내 블로그 (BLG-06). owned = 블로그장, joined = 부블로그장·멤버. */
public record MyBlogsResponse(List<Item> owned, List<Item> joined) {

    public record Item(
            String slug,
            String name,
            String coverImageUrl,
            BlogVisibility visibility,
            BlogRole role,
            int memberCount,
            OffsetDateTime createdAt) {
    }
}
