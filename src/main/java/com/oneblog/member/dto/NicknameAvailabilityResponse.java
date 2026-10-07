package com.oneblog.member.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** reason: TAKEN, RESERVED, INVALID_FORMAT. 사용할 수 있으면 생략. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NicknameAvailabilityResponse(boolean available, String reason) {

    public static NicknameAvailabilityResponse ok() {
        return new NicknameAvailabilityResponse(true, null);
    }

    public static NicknameAvailabilityResponse unavailable(String reason) {
        return new NicknameAvailabilityResponse(false, reason);
    }
}
