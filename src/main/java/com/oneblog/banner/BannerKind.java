package com.oneblog.banner;

/** 관리자가 넣는 배너의 종류 (ERD banners.kind, D-133). 화면에는 label이 배너 표시로 붙는다. */
public enum BannerKind {
    AD("광고"), NOTICE("공지"), EVENT("이벤트");

    private final String label;

    BannerKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 모르는 값이면 null. 빈 값이면 광고. */
    static BannerKind parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AD;
        }
        for (BannerKind kind : values()) {
            if (kind.name().equals(raw.strip())) {
                return kind;
            }
        }
        return null;
    }
}
