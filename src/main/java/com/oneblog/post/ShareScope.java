package com.oneblog.post;

import com.oneblog.blog.BlogVisibility;

/**
 * 글 공유 범위: 공유 창에 어떤 항목을 보일지 (031, BRD-07, 6.2).
 * - PUBLIC: 비회원이 key 없이 볼 수 있는 글 → 주소 칸·복사, X·텔레그램·페이스북, 기기 공유
 * - LINK: 일부 공개 블로그의 글(링크를 받은 사람만) → 주소 칸·복사, 기기 공유. 공개로 퍼지는 SNS는 숨긴다
 * - MEMBERS: 비공개 블로그, 또는 관리자가 숨긴 블로그·글을 멤버·관리자가 보는 경우 → 주소 칸·복사만
 * 판단은 서버가 하고 화면은 이 값만 따른다.
 */
public enum ShareScope {
    PUBLIC, LINK, MEMBERS;

    public static ShareScope of(BlogVisibility visibility, boolean blogHidden, boolean postHidden) {
        if (blogHidden || postHidden) {
            return MEMBERS;
        }
        return switch (visibility) {
            case PUBLIC -> PUBLIC;
            case UNLISTED -> LINK;
            case PRIVATE -> MEMBERS;
        };
    }
}
