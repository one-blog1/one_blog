package com.oneblog.blog.join;

import java.time.OffsetDateTime;

/** 블로그장이 보는 대기 중인 참여 신청 한 건. 신청자는 닉네임만 보여준다 (4.4). */
public record JoinRequestItem(Long id, String nickname, OffsetDateTime requestedAt) {
}
