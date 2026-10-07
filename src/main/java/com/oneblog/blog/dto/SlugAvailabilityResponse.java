package com.oneblog.blog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 주소 확인 결과. reason: INVALID_FORMAT, RESERVED, TAKEN */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SlugAvailabilityResponse(String slug, boolean available, String reason) {

    public static SlugAvailabilityResponse ok(String slug) {
        return new SlugAvailabilityResponse(slug, true, null);
    }

    public static SlugAvailabilityResponse unavailable(String slug, String reason) {
        return new SlugAvailabilityResponse(slug, false, reason);
    }
}
