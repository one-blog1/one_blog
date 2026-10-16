package com.oneblog.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * IP·회원별 요청 제한 (D-80, USR-08). DB에 기록하는 부분은 RateLimiterIntegrationTest (032).
 * 여기서는 DB 없이 확인할 수 있는 것만 본다.
 */
class RateLimiterTest {

    @Test
    void 꺼져_있으면_검사하지_않는다() {
        RateLimiter limiter = new RateLimiter(false, null, null, "test-pepper");
        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                limiter.check("login", "ip", 1, Duration.ofMinutes(1));
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void 대상은_비밀_값을_섞은_64자_해시로_바꾼다() {
        RateLimiter limiter = new RateLimiter(false, null, null, "test-pepper");
        String hash = limiter.hash("1.2.3.4");
        assertThat(hash).hasSize(64).doesNotContain("1.2.3.4").isEqualTo(limiter.hash("1.2.3.4"));
        // 같은 IP라도 비밀 값이 다르면 다른 해시 (IPv4 전체를 미리 계산해 맞힐 수 없게)
        assertThat(new RateLimiter(false, null, null, "other-pepper").hash("1.2.3.4")).isNotEqualTo(hash);
    }
}
