package com.oneblog.report;

/** 신고 사유 (SOC-06: 사유를 골라 신고). */
public enum ReportReason {
    SPAM("스팸·광고"),
    ABUSE("욕설·괴롭힘"),
    OBSCENE("음란·선정적인 내용"),
    PRIVACY("개인정보 노출"),
    ILLEGAL("불법 정보"),
    ETC("기타");

    private final String label;

    ReportReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
