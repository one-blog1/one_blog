package com.oneblog.banner;

/**
 * 메인 배너 한 장 (BRD-12). kind는 POPULAR(인기 글, 자동) 또는 AD(광고, 관리자).
 * external이면 바깥 주소라 화면이 새 창으로 연다.
 */
public record BannerSlide(String kind, String label, String title, String caption, String imageUrl, String linkUrl,
        boolean external) {
}
