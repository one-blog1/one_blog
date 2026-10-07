package com.oneblog.member;

import java.util.Set;

/**
 * 보는 사람이 가린 작성자 (차단 SOC-05, 013). 블로그 안의 글·댓글 목록이 쓴다.
 * 같은 블로그의 멤버끼리는 차단해도 그 블로그 안에서는 보이므로(D-36), 부르는 쪽이 멤버가 아닐 때만 적용한다.
 */
public interface HiddenAuthors {

    /** 비회원이면 빈 집합. */
    Set<Long> of(Long viewerId);
}
