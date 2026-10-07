package com.oneblog.common.web;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** DB의 LocalDateTime을 응답용 OffsetDateTime(서버 시간대)으로 바꾼다. */
public final class Times {

    private Times() {
    }

    public static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
