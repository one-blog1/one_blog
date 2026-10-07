package com.oneblog.blog.dto;

import java.util.List;

import com.oneblog.blog.BlogJoinPolicy;
import com.oneblog.blog.BlogVisibility;

/** 블로그 만들기 요청 (contracts/blog-api.md). 규칙 검사는 서비스가 한다 (BlogPolicy, TagPolicy). */
public record CreateBlogRequest(
        String name,
        String slug,
        String description,
        Long coverFileId,
        List<String> tags,
        BlogVisibility visibility,
        BlogJoinPolicy joinPolicy) {
}
