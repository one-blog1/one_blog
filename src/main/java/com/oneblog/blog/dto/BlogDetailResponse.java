package com.oneblog.blog.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.oneblog.blog.BlogJoinPolicy;
import com.oneblog.blog.BlogRole;
import com.oneblog.blog.BlogVisibility;

/**
 * 블로그 첫 화면 정보 (BLG-01). 볼 수 있는 사람에게만 만든다 (BlogAccessService).
 * myRole은 멤버가 아니면 null, shareUrl은 일부 공개 블로그의 멤버에게만 있다.
 * permissions는 멤버에게만 있다. 화면이 관리 버튼을 보일지 정하는 데만 쓰고, 실제 권한은 API마다 다시 확인한다 (SEC-07).
 */
public record BlogDetailResponse(
        String slug,
        String name,
        String description,
        String coverImageUrl,
        List<String> tags,
        BlogVisibility visibility,
        BlogJoinPolicy joinPolicy,
        int memberCount,
        String ownerNickname,
        OffsetDateTime createdAt,
        BlogRole myRole,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shareUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) Permissions permissions) {

    /** 블로그장은 모두 true, 부블로그장은 받은 권한만 (2장, D-71). */
    public record Permissions(boolean canEditInfo, boolean canManageMembers, boolean canManagePosts) {
    }
}
