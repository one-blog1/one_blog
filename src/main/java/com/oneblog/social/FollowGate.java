package com.oneblog.social;

/** 팔로우하기 전 검사 (예: 차단한 사이면 팔로우할 수 없음, 013). 막을 때는 예외를 던진다. */
public interface FollowGate {

    void check(Long followerId, Long followeeId);
}
