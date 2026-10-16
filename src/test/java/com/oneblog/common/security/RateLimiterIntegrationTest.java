package com.oneblog.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.oneblog.IntegrationTestSupport;
import com.oneblog.batch.CleanupTask;
import com.oneblog.common.web.ApiException;

/**
 * 요청 제한 기록을 DB에 두어 운영 앱 2개가 같이 센다 (032, SEC-03, D-80, SCL-01).
 * 테스트 설정은 요청 제한을 꺼 두므로(app.rate-limit.enabled=false) 여기서는 켠 RateLimiter를 직접 만든다.
 * 같은 DB를 쓰는 RateLimiter 두 개 = 앱 두 개.
 */
class RateLimiterIntegrationTest extends IntegrationTestSupport {

    private static final String LOGIN = "login";
    private static final String FIND_EMAIL = "find-email";
    private static final String IP = "1.2.3.4";
    private static final String OTHER_IP = "5.6.7.8";
    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final String TOO_MANY = "요청이 너무 많습니다";
    private static final String COUNT_ROWS = "SELECT COUNT(*) FROM rate_limit_hits";

    @Autowired
    private NamedParameterJdbcTemplate named;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private CleanupTask cleanupTask;

    private RateLimiter app() {
        return new RateLimiter(true, named, transactionManager, "test-only-privacy-pepper");
    }

    @Test
    void 앱_두_개가_같은_기록으로_센다() {
        RateLimiter a = app();
        RateLimiter b = app();
        // 같은 IP의 로그인 30번이 두 앱에 번갈아 들어온다 (10분 30번, SEC-03)
        for (int i = 0; i < 30; i++) {
            (i % 2 == 0 ? a : b).check(LOGIN, IP, 30, TEN_MINUTES);
        }
        // 31번째는 어느 앱으로 가든 막힌다
        assertThatThrownBy(() -> a.check(LOGIN, IP, 30, TEN_MINUTES))
                .isInstanceOf(ApiException.class).hasMessageContaining(TOO_MANY);
        assertThatThrownBy(() -> b.check(LOGIN, IP, 30, TEN_MINUTES))
                .isInstanceOf(ApiException.class).hasMessageContaining(TOO_MANY);
        // 다른 IP, 다른 제한은 따로 센다
        assertThatCode(() -> a.check(LOGIN, OTHER_IP, 30, TEN_MINUTES)).doesNotThrowAnyException();
        assertThatCode(() -> b.check(FIND_EMAIL, IP, 5, TEN_MINUTES)).doesNotThrowAnyException();
    }

    @Test
    void 창이_지나면_다시_받고_지난_기록은_지운다() {
        RateLimiter limiter = app();
        for (int i = 0; i < 5; i++) {
            limiter.check(FIND_EMAIL, IP, 5, TEN_MINUTES);
        }
        assertThatThrownBy(() -> limiter.check(FIND_EMAIL, IP, 5, TEN_MINUTES)).isInstanceOf(ApiException.class);

        // 10분이 지난 것처럼 기록 시각을 옮긴다
        jdbc.update("UPDATE rate_limit_hits SET created_at = NOW(6) - INTERVAL 11 MINUTE");
        assertThatCode(() -> limiter.check(FIND_EMAIL, IP, 5, TEN_MINUTES)).doesNotThrowAnyException();
        // 창 밖 기록 5줄은 지워지고 새 기록 1줄만 남는다
        assertThat(jdbc.queryForObject(COUNT_ROWS, Integer.class)).isEqualTo(1);
    }

    @Test
    void 대상은_원문이_아니라_해시로_남긴다() {
        app().check(LOGIN, IP, 30, TEN_MINUTES);
        String stored = jdbc.queryForObject("SELECT key_hash FROM rate_limit_hits", String.class);
        assertThat(stored).hasSize(64).doesNotContain(IP);
        assertThat(jdbc.queryForObject("SELECT action FROM rate_limit_hits", String.class)).isEqualTo(LOGIN);
    }

    @Test
    void 부르는_쪽이_되돌려져도_시도는_세어진다() {
        // 비밀번호 재설정·신고처럼 뒤에서 실패해 트랜잭션을 되돌려도 기록은 남아야 매크로를 센다 (research R4)
        RateLimiter limiter = app();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            limiter.check(FIND_EMAIL, IP, 5, TEN_MINUTES);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject(COUNT_ROWS, Integer.class)).isEqualTo(1);
    }

    @Test
    void 매일_정리에서_1시간_지난_기록을_지운다() {
        RateLimiter limiter = app();
        limiter.check(LOGIN, IP, 30, TEN_MINUTES);
        limiter.check(LOGIN, OTHER_IP, 30, TEN_MINUTES);
        jdbc.update("UPDATE rate_limit_hits SET created_at = NOW(6) - INTERVAL 2 HOUR WHERE key_hash = ?",
                limiter.hash(IP));

        cleanupTask.run(LocalDateTime.now());

        assertThat(jdbc.queryForObject(COUNT_ROWS, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT key_hash FROM rate_limit_hits", String.class))
                .isEqualTo(limiter.hash(OTHER_IP));
    }
}
