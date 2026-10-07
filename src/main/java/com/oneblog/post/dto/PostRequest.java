package com.oneblog.post.dto;

import java.util.List;

/**
 * 글 쓰기·고치기 요청. notice는 블로그 공지(글 관리 권한 필요).
 * categoryId(008), tags(008), imageFileIds(005)는 해당 기능에서 쓴다.
 */
public record PostRequest(
        String title,
        String content,
        Boolean notice,
        Long categoryId,
        List<String> tags,
        List<Long> imageFileIds) {
}
