package com.oneblog.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import com.oneblog.blog.BlogTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * 04:00 배치는 서버가 여러 대여도 하루 한 번만 돈다 (SCL-03, 3.2 팀 결정, 029 FR-010).
 * 운영은 앱이 2개 동시에 떠 있고 둘 다 04:00에 배치를 시작한다. 잠금을 먼저 잡은 쪽만 돌고, 다른 쪽은 건너뛴다.
 * 서버 두 대는 주인 이름이 다른 SchedulerLock 두 개로 흉내 낸다.
 */
class SchedulerLockIntegrationTest extends BlogTestSupport {

    private static final String NAME = "test-lock";

    @Autowired
    private NamedParameterJdbcTemplate namedJdbc;

    @Autowired
    private DailyBatch dailyBatch;

    private SchedulerLock serverA;
    private SchedulerLock serverB;

    @BeforeEach
    void servers() {
        serverA = new SchedulerLock(namedJdbc, "server-a");
        serverB = new SchedulerLock(namedJdbc, "server-b");
    }

    @Test
    void 한_쪽이_잡으면_다른_쪽은_못_잡는다() {
        assertThat(serverA.tryLock(NAME, Duration.ofHours(1))).isTrue();
        assertThat(serverB.tryLock(NAME, Duration.ofHours(1))).isFalse();
    }

    @Test
    void 잠금_시간이_지나면_다른_서버가_잡는다() {
        // 잡은 서버가 멈춰 풀지 못해도 최대 시간(lock_until)이 지나면 다른 서버가 잡는다
        assertThat(serverA.tryLock(NAME, Duration.ofHours(1))).isTrue();
        jdbc.update("UPDATE shedlock SET lock_until = ? WHERE name = ?", LocalDateTime.now().minusSeconds(1), NAME);
        assertThat(serverB.tryLock(NAME, Duration.ofHours(1))).isTrue();
    }

    @Test
    void 다른_서버의_잠금은_풀_수_없다() {
        assertThat(serverA.tryLock(NAME, Duration.ofHours(1))).isTrue();
        serverB.release(NAME);
        assertThat(new SchedulerLock(namedJdbc, "server-c").tryLock(NAME, Duration.ofHours(1))).isFalse();
    }

    @Test
    void 배치를_끝낸_뒤_5분_안에는_다른_서버가_못_잡는다() {
        // 한 서버가 04:00 배치를 금방 끝내고 잠금을 풀어도, 조금 늦게 온 다른 서버가 같은 날 한 번 더 돌면 안 된다.
        // 고치기 전에는 끝나자마자 잠금이 풀려 이 테스트가 실패했다 (FR-010)
        dailyBatch.scheduled();
        assertThat(serverB.tryLock(DailyBatch.LOCK_NAME, Duration.ofHours(1))).isFalse();
        // 5분이 지나면(다음 날 실행이나 수동 실행) 다시 잡을 수 있다
        jdbc.update("UPDATE shedlock SET lock_until = ? WHERE name = ?", LocalDateTime.now().minusSeconds(1),
                DailyBatch.LOCK_NAME);
        assertThat(serverB.tryLock(DailyBatch.LOCK_NAME, Duration.ofHours(1))).isTrue();
    }

    @Test
    void 잠겨_있으면_예약_실행은_배치를_건너뛴다() throws Exception {
        Cookie owner = signUpAndLogin("owner@example.com", "블로그장");
        createBlog(owner, "due-blog", "PUBLIC").andExpect(status().isCreated());
        mvc.perform(post("/api/blogs/due-blog/close").with(csrf()).cookie(owner)).andExpect(status().isOk());
        jdbc.update("UPDATE blogs SET close_scheduled_at = ? WHERE slug = 'due-blog'", LocalDateTime.now().minusMinutes(1));

        assertThat(serverB.tryLock(DailyBatch.LOCK_NAME, Duration.ofHours(1))).isTrue();
        dailyBatch.scheduled();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE slug = 'due-blog'", String.class))
                .as("다른 서버가 잡고 있어 건너뛴다").isEqualTo("CLOSING");

        jdbc.update("UPDATE shedlock SET lock_until = ? WHERE name = ?", LocalDateTime.now().minusSeconds(1),
                DailyBatch.LOCK_NAME);
        dailyBatch.scheduled();
        assertThat(jdbc.queryForObject("SELECT status FROM blogs WHERE slug = 'due-blog'", String.class))
                .isEqualTo("CLOSED");
    }

    @Test
    void 매일_새벽_4시_한국_시간에_돈다() throws Exception {
        Scheduled scheduled = DailyBatch.class.getMethod("scheduled").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
