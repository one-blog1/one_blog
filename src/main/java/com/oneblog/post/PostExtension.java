package com.oneblog.post;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.dto.PostRequest;

/**
 * 글에 붙는 부가 기능(이미지 005, 좋아요 006, 카테고리·태그 008, 조회수 014)이 글 저장·표시 흐름에 끼어드는 자리.
 * 글 서비스가 기능마다 바뀌지 않게, 각 기능이 이 인터페이스를 구현한 빈을 하나씩 둔다.
 */
public interface PostExtension {

    /** 저장 전 검사 (예: 카테고리가 이 블로그 것인지, 태그 규칙). 실패하면 예외. */
    default void validate(Blog blog, PostRequest request) {
    }

    /** 글을 저장(새로 쓰기·고치기)한 뒤 (예: 태그·이미지 연결). */
    default void afterSave(Blog blog, Post post, PostRequest request, AuthenticatedUser principal) {
    }

    /** 글을 지운 뒤 (예: 작성자 알림). */
    default void afterDelete(Blog blog, Post post, AuthenticatedUser principal) {
    }

    /** 상세 화면 정보 채우기. principal은 비회원이면 null. */
    default void describe(Blog blog, Post post, AuthenticatedUser principal, PostView view) {
    }

    /**
     * 목록 정보 채우기 (한 번의 조회로 여러 글을).
     * 여러 블로그의 글이 섞인 목록(태그·검색·피드, PostCardService)에서는 blog가 null이다.
     */
    default void describeList(Blog blog, PostListContext context) {
    }
}
