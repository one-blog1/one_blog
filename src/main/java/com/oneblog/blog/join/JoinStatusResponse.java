package com.oneblog.blog.join;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 블로그에 대한 내 참여 상태.
 * status: NONE(아무것도 없음), PENDING(승인 대기), MEMBER(멤버), REJECTED(거절됨, retryAt부터 다시 신청 가능)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JoinStatusResponse(String status, OffsetDateTime retryAt) {

    public static JoinStatusResponse of(String status) {
        return new JoinStatusResponse(status, null);
    }
}
