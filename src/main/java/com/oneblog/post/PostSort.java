package com.oneblog.post;

/** 블로그 글 목록 정렬 (BRD-02, D-129). 인기순은 좋아요 많은 순 (D-89). 모르는 값은 최신순. */
public enum PostSort {
    LATEST("latest"),
    POPULAR("popular");

    private final String value;

    PostSort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static PostSort from(String value) {
        return "popular".equals(value) ? POPULAR : LATEST;
    }
}
