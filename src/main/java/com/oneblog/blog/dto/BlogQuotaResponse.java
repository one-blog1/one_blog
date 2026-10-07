package com.oneblog.blog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 남은 생성 개수 (BLG-10). public은 공개 + 일부 공개 (D-51). */
public record BlogQuotaResponse(
        @JsonProperty("public") Quota publicQuota,
        @JsonProperty("private") Quota privateQuota) {

    public record Quota(long used, int limit, long remaining) {

        public static Quota of(long used, int limit) {
            return new Quota(used, limit, Math.max(0, limit - used));
        }
    }
}
