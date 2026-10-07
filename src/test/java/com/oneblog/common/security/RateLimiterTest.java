package com.oneblog.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.oneblog.common.web.ApiException;

/** IP·회원별 요청 제한 (D-80, USR-08: 같은 IP 10분에 5번). */
class RateLimiterTest {

    @Test
    void 창_안에서_정한_횟수까지만_통과하고_시간이_지나면_다시_된다() {
        RateLimiter limiter = new RateLimiter(true);
        Duration window = Duration.ofMinutes(10);
        long t = 1_000_000L;
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("find-email:1.2.3.4", 5, window, t + i)).isTrue();
        }
        assertThat(limiter.tryAcquire("find-email:1.2.3.4", 5, window, t + 10)).isFalse();
        // 다른 IP는 따로 센다
        assertThat(limiter.tryAcquire("find-email:5.6.7.8", 5, window, t + 10)).isTrue();
        // 10분이 지나면 가장 오래된 기록이 빠진다
        assertThat(limiter.tryAcquire("find-email:1.2.3.4", 5, window, t + window.toMillis() + 1)).isTrue();
    }

    @Test
    void 넘으면_429와_다시_시도할_시간을_준다() {
        RateLimiter limiter = new RateLimiter(true);
        limiter.check("login", "ip", 1, Duration.ofMinutes(1));
        assertThatThrownBy(() -> limiter.check("login", "ip", 1, Duration.ofMinutes(1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("요청이 너무 많습니다");
    }

    @Test
    void 꺼져_있으면_검사하지_않는다() {
        RateLimiter limiter = new RateLimiter(false);
        for (int i = 0; i < 10; i++) {
            limiter.check("login", "ip", 1, Duration.ofMinutes(1));
        }
    }
}
