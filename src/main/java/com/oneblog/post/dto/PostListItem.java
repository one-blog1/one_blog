package com.oneblog.post.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 글 목록의 한 줄 (BRD-02). 썸네일은 글의 첫 이미지 주소(005), 없으면 null.
 * 본문 첫 미디어가 영상 링크면 썸네일은 서버가 받아 둔 영상 썸네일이고 thumbnailVideo에 사이트(youtube, chzzk, soop)가 온다 (D-128).
 * excerpt는 본문 앞부분 글자, authorImageUrl은 작성자 프로필 사진(없으면 null).
 */
public record PostListItem(
        Long id,
        String title,
        String authorName,
        String authorImageUrl,
        boolean notice,
        String categoryName,
        List<String> tags,
        String thumbnailUrl,
        String thumbnailVideo,
        String excerpt,
        int viewCount,
        int likeCount,
        int commentCount,
        OffsetDateTime createdAt) {
}
