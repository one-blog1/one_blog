package com.oneblog.post;

/** 글 종류 (ERD posts.post_type). MAIN_NOTICE는 메인 관리자 공지(BRD-10, blog_id 없음). */
public enum PostType {
    BLOG, BLOG_NOTICE, MAIN_NOTICE
}
