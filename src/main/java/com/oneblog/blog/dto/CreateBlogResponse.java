package com.oneblog.blog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 블로그 만들기 응답: 새 블로그 ID·주소, 일부 공개면 공유 링크 (BLG-01). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBlogResponse(Long id, String slug, String url, String shareUrl) {
}
