package com.oneblog.banner;

import java.util.List;

/**
 * 배너 인기 글 설정 (6.8, D-132). count 0이면 인기 글을 넣지 않는다.
 * @param popularCount 0~5
 * @param popularDays 1·7·30
 */
public record BannerSettings(int popularCount, int popularDays) {

    static final String COUNT_KEY = "BANNER_POPULAR_COUNT";
    static final String DAYS_KEY = "BANNER_POPULAR_DAYS";
    static final int DEFAULT_COUNT = 3;
    static final int DEFAULT_DAYS = 7;
    static final int MAX_COUNT = 5;
    static final List<Integer> DAYS = List.of(1, 7, 30);
    /** 노출을 켠 광고 중 앞에서부터 이만큼 (6.8). */
    static final int MAX_ADS = 5;

    static BannerSettings read(SiteSettings settings) {
        int count = settings.getInt(COUNT_KEY, DEFAULT_COUNT);
        int days = settings.getInt(DAYS_KEY, DEFAULT_DAYS);
        return new BannerSettings(count < 0 || count > MAX_COUNT ? DEFAULT_COUNT : count,
                DAYS.contains(days) ? days : DEFAULT_DAYS);
    }

    /** 인기 글 표시 이름 (6.8). */
    String popularLabel() {
        return switch (popularDays) {
            case 1 -> "오늘 인기";
            case 30 -> "이번 달 인기";
            default -> "이번 주 인기";
        };
    }
}
