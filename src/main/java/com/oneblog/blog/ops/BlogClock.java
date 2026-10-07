package com.oneblog.blog.ops;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 폐쇄 시각 계산 (BLG-09, D-12). 기준은 한국 시간: 버튼을 누른 날짜 + 7일, 새벽 4시.
 * DB에는 서버 시간대의 LocalDateTime으로 저장하므로 바꿔서 돌려준다 (AWS 서버 기본 시간대는 UTC).
 */
public final class BlogClock {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final LocalTime CLOSE_TIME = LocalTime.of(4, 0);
    public static final int CLOSE_AFTER_DAYS = 7;

    private BlogClock() {
    }

    /** now(서버 시간대)에 누른 폐쇄의 예정 시각 (서버 시간대). */
    public static LocalDateTime closeAt(LocalDateTime now) {
        LocalDate seoulDate = now.atZone(ZoneId.systemDefault()).withZoneSameInstant(SEOUL).toLocalDate();
        return ZonedDateTime.of(seoulDate.plusDays(CLOSE_AFTER_DAYS), CLOSE_TIME, SEOUL)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 서버 시간대 시각을 한국 날짜로. 폐쇄 n일 전 알림을 고를 때 쓴다. */
    public static LocalDate seoulDate(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(SEOUL).toLocalDate();
    }
}
