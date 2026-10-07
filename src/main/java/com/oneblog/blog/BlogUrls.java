package com.oneblog.blog;

/** 블로그 주소 만들기 (D-70). slug는 영문 소문자·숫자·-만 있어 인코딩이 필요 없다. */
public final class BlogUrls {

    private BlogUrls() {
    }

    public static String blogUrl(String slug) {
        return "/blog/" + slug;
    }

    /** 일부 공개 블로그의 공유 링크 (research R6). */
    public static String shareUrl(Blog blog) {
        if (blog.getVisibility() != BlogVisibility.UNLISTED || blog.getShareToken() == null) {
            return null;
        }
        return blogUrl(blog.getSlug()) + "?key=" + blog.getShareToken();
    }
}
