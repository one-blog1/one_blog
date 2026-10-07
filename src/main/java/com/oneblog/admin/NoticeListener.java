package com.oneblog.admin;

import com.oneblog.post.Post;

/** 공지를 올린 뒤 처리(전체 회원 알림, 011)가 끼어드는 자리. */
public interface NoticeListener {

    void noticePublished(Post notice);
}
