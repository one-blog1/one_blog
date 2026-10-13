package com.oneblog.banner;

import java.util.List;

/**
 * 배너에 자동으로 넣는 종류마다 개수·기간 (6.8, D-132, D-133). 개수 0이면 그 종류는 넣지 않는다.
 * site_settings의 BANNER_{POPULAR|NEW_BLOG|POPULAR_BLOG}_COUNT·_DAYS. 없거나 잘못된 값이면 기본값.
 */
public record BannerSettings(Auto popular, Auto newBlog, Auto popularBlog) {

    /** @param count 0~5  @param days 1·7·30 */
    public record Auto(int count, int days) {
    }

    static final int MAX_COUNT = 5;
    static final List<Integer> DAYS = List.of(1, 7, 30);
    /** 노출을 켠 관리자 배너(광고·공지·이벤트) 중 앞에서부터 이만큼 (6.8). */
    static final int MAX_MANUAL = 8;

    static final String POPULAR = "POPULAR";
    static final String NEW_BLOG = "NEW_BLOG";
    static final String POPULAR_BLOG = "POPULAR_BLOG";

    static BannerSettings read(SiteSettings settings) {
        return new BannerSettings(read(settings, POPULAR, 3, 7), read(settings, NEW_BLOG, 2, 30),
                read(settings, POPULAR_BLOG, 2, 30));
    }

    private static Auto read(SiteSettings settings, String name, int defaultCount, int defaultDays) {
        int count = settings.getInt(countKey(name), defaultCount);
        int days = settings.getInt(daysKey(name), defaultDays);
        return new Auto(validCount(count) ? count : defaultCount, valid(days) ? days : defaultDays);
    }

    static void write(SiteSettings settings, String name, Auto auto) {
        settings.put(countKey(name), String.valueOf(auto.count()));
        settings.put(daysKey(name), String.valueOf(auto.days()));
    }

    static String countKey(String name) {
        return "BANNER_" + name + "_COUNT";
    }

    static String daysKey(String name) {
        return "BANNER_" + name + "_DAYS";
    }

    static boolean validCount(Integer count) {
        return count != null && count >= 0 && count <= MAX_COUNT;
    }

    static boolean valid(Integer days) {
        return days != null && DAYS.contains(days);
    }

    /** 인기 글 표시 이름 (6.8). */
    static String popularLabel(int days) {
        return switch (days) {
            case 1 -> "오늘 인기";
            case 30 -> "이번 달 인기";
            default -> "이번 주 인기";
        };
    }
}
