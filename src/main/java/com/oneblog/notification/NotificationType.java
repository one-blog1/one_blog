package com.oneblog.notification;

/**
 * 알림 종류와 탭, 끌 수 있는지 (3.6). 계정·블로그 운영에 꼭 필요한 알림은 끌 수 없다.
 * 탭: COMMENT(댓글), LIKE(좋아요), FOLLOW(팔로우·구독), BLOG(블로그), OPERATION(운영).
 */
public enum NotificationType {
    COMMENT("COMMENT", true, "내 글에 댓글"),
    REPLY("COMMENT", true, "내 댓글에 대댓글"),
    LIKE("LIKE", true, "내 글 좋아요"),
    FOLLOW("FOLLOW", true, "새 팔로워"),
    BLOG_SUBSCRIBE("FOLLOW", true, "블로그 구독"),
    JOIN_REQUEST("BLOG", true, "참여 신청"),
    JOIN_RESULT("BLOG", true, "참여 승인·거절"),
    TRANSFER_REQUEST("BLOG", false, "위임 요청"),
    TRANSFER_RESULT("BLOG", false, "위임 수락·거절·자동 취소"),
    BLOG_CLOSING("BLOG", false, "폐쇄 예정"),
    BLOG_CLOSE_CANCELED("BLOG", false, "폐쇄 철회"),
    BLOG_PRIVATE("BLOG", false, "비공개 전환"),
    BLACKLIST_RESULT("BLOG", false, "블랙리스트 해제 문의 결과"),
    POST_DELETED("OPERATION", false, "내 글 삭제됨"),
    COMMENT_DELETED("OPERATION", false, "내 댓글 삭제됨"),
    REPORT_RESULT("OPERATION", true, "신고 처리 결과"),
    SANCTION("OPERATION", false, "경고·정지·강제 퇴장"),
    OWNER_SANCTION("OPERATION", false, "블로그장 경고·권한 박탈"),
    NOTICE("OPERATION", true, "공지사항");

    private final String tab;
    private final boolean optional;
    private final String label;

    NotificationType(String tab, boolean optional, String label) {
        this.tab = tab;
        this.optional = optional;
        this.label = label;
    }

    public String tab() {
        return tab;
    }

    /** 끌 수 있는 알림인지 (3.6 "끌 수 있음" 열). */
    public boolean optional() {
        return optional;
    }

    public String label() {
        return label;
    }
}
