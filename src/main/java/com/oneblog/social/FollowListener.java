package com.oneblog.social;

/** 팔로우가 생겼을 때 (알림 011). */
public interface FollowListener {

    void followed(Long followerId, Long followeeId);
}
