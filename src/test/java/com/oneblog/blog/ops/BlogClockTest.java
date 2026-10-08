package com.oneblog.blog.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/** 폐쇄 시각: 누른 날짜(한국) + 7일 새벽 4시 (BLG-09, D-12). */
class BlogClockTest {

    private static LocalDateTime serverTime(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, BlogClock.SEOUL)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private static ZonedDateTime inSeoul(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(BlogClock.SEOUL);
    }

    @Test
    void 오후에_누르면_7일_뒤_새벽_4시() {
        ZonedDateTime at = inSeoul(BlogClock.closeAt(serverTime(10, 2, 15)));
        assertThat(at.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(at.toLocalTime()).isEqualTo(LocalTime.of(4, 0));
    }

    @Test
    void 새벽_1시에_눌러도_날짜_기준() {
        ZonedDateTime at = inSeoul(BlogClock.closeAt(serverTime(10, 2, 1)));
        assertThat(at.toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 9));
    }
}
