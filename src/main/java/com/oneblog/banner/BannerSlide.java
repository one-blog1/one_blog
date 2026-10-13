package com.oneblog.banner;

/**
 * 메인 배너 한 장 (BRD-12). kind: 자동 POPULAR(인기 글)·NEW_BLOG(새 블로그)·POPULAR_BLOG(인기 블로그),
 * 관리자 AD(광고)·NOTICE(공지)·EVENT(이벤트). external이면 바깥 주소라 화면이 새 창으로 연다.
 * blogSlug는 블로그 배너에만 있다: 대표 이미지가 없으면 화면이 그 블로그 색과 첫 글자로 채운다.
 */
public record BannerSlide(String kind, String label, String title, String caption, String imageUrl, String linkUrl,
        boolean external, String blogSlug) {
}
