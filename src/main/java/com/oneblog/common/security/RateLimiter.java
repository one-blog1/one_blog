package com.oneblog.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.oneblog.common.web.ApiException;

/**
 * IP·회원별 요청 제한 (D-80, 6.6 "트래픽 제한", USR-08 같은 IP 10분에 5번).
 * 최근 N분 동안의 요청을 세는 슬라이딩 윈도. 기록은 DB(rate_limit_hits)에 두어 운영 앱 2개(D-135)가 같이 센다
 * (032, SCL-01, D-143). 대상(IP·회원 ID)은 원문 대신 비밀 값을 섞은 HMAC 해시로 둔다 (REQ-002).
 * - 기록·세기는 부르는 쪽과 따로 커밋한다(REQUIRES_NEW): 뒤에서 실패해 되돌려져도 시도는 세어진다
 * - 시각은 DB 시각(NOW(6))으로 센다: 앱마다 시계가 조금 달라도 기준이 하나
 * - DB 오류면 경고만 남기고 통과시킨다: 요청 제한은 보조 장치라 로그인 전체를 막지 않는다
 * - 1시간 지난 기록은 04:00 배치(CleanupTask)가 지운다
 * app.rate-limit.enabled=false면 검사하지 않는다(테스트: 같은 IP로 수백 번 로그인한다).
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String HMAC = "HmacSHA256";

    private final boolean enabled;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final byte[] pepper;

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled, NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            @Value("${app.privacy.hash-pepper:${app.verification.code-pepper}}") String pepper) {
        this.enabled = enabled;
        this.jdbc = jdbc;
        this.tx = transactionManager == null ? null : separateTransaction(transactionManager);
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    private static TransactionTemplate separateTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /** 넘으면 429 TOO_MANY_REQUESTS (retryAfterSeconds 포함). */
    public void check(String action, String key, int max, Duration window) {
        if (!enabled) {
            return;
        }
        if (!tryAcquire(action, hash(key), max, window)) {
            throw ApiException.withRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", Math.max(1, window.toSeconds() / max));
        }
    }

    /** 창 밖 기록을 지우고, 창 안 기록이 max개보다 적으면 한 줄 더하고 true. 테스트에서 바로 부른다. */
    boolean tryAcquire(String action, String keyHash, int max, Duration window) {
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("action", action)
                .addValue("key", keyHash)
                .addValue("micros", window.toNanos() / 1_000);
        try {
            Boolean allowed = tx.execute(status -> {
                jdbc.update("DELETE FROM rate_limit_hits WHERE action = :action AND key_hash = :key"
                        + " AND created_at <= NOW(6) - INTERVAL :micros MICROSECOND", args);
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_hits"
                        + " WHERE action = :action AND key_hash = :key", args, Integer.class);
                if (count != null && count >= max) {
                    return false;
                }
                jdbc.update("INSERT INTO rate_limit_hits (action, key_hash) VALUES (:action, :key)", args);
                return true;
            });
            return !Boolean.FALSE.equals(allowed);
        } catch (DataAccessException e) {
            log.warn("요청 제한 기록을 읽거나 쓰지 못해 통과시킵니다: {}", action, e);
            return true;
        }
    }

    /** 대상(IP·회원 ID)의 HMAC-SHA256 (16진수 64자). */
    String hash(String key) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(pepper, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(("rate:" + key).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
