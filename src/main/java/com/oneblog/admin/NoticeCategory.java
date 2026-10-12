package com.oneblog.admin;

import java.util.Arrays;

/** 메인 공지 종류 (BRD-10, D-122, ERD posts.notice_category). 화면에 보이는 이름은 label. */
public enum NoticeCategory {
    GENERAL("일반"), UPDATE("업데이트"), FIX("수정"), MAINTENANCE("점검");

    private final String label;

    NoticeCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 비어 있으면 일반, 정해진 값이 아니면 null (부르는 쪽이 400으로 거부). */
    public static NoticeCategory parseOrDefault(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        return Arrays.stream(values()).filter(c -> c.name().equals(raw.trim())).findFirst().orElse(null);
    }
}
