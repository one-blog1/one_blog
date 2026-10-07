package com.oneblog.post;

/** 글을 지운 주체 (ERD posts.deleted_by). 작성자가 아닌 사람이 지우면 작성자에게 알린다 (BRD-01). */
public enum DeletedBy {
    AUTHOR, BLOG_OWNER, ADMIN
}
