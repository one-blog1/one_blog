package com.oneblog.file;

/** 업로드 용도 (ERD files.purpose). */
public enum FilePurpose {
    POST, PROFILE, BLOG_COVER,
    /** 글 본문 첫 영상 링크의 썸네일. 서버가 영상 사이트에서 받아 그 글에 연결한다 (D-128) */
    VIDEO_THUMB
}
