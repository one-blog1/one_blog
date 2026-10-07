package com.oneblog.blog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateBlogResponse(Long id, String slug, String url, String shareUrl) {
}
