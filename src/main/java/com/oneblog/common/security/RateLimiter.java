package com.oneblog.common.security;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oneblog.common.web.ApiException;

/**
 * IP·회원별 요청 제한 (D-80, 6.6 "트래픽 제한", USR-08 같은 IP 10분에 5번).
 * 최근 시각들을 기억하는 슬라이딩 윈도. 서버 메모리에 두므로 서버마다 따로 센다.
 * 이중화(SCL) 때는 서버 대수만큼 느슨해지니 공유 저장소(DB·Redis)로 옮긴다.
 * app.rate-limit.enabled=false면 검사하지 않는다(테스트: 같은 IP로 수백 번 로그인한다).
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS = 100_000;

    private final boolean enabled;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(@Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** 넘으면 429 TOO_MANY_REQUESTS (retryAfterSeconds 포함). */
    public void check(String action, String key, int max, Duration window) {
        if (!enabled) {
            return;
        }
        if (!tryAcquire(action + ":" + key, max, window, System.currentTimeMillis())) {
            throw ApiException.withRetryAfter(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", Math.max(1, window.toSeconds() / max));
        }
    }

    /** 테스트에서 바로 부를 수 있게 나눈 판단 부분. */
    boolean tryAcquire(String bucket, int max, Duration window, long nowMillis) {
        if (hits.size() > MAX_KEYS) {
            // 키가 너무 많아지면(공격 등) 오래된 기록을 버려 메모리를 지킨다
            hits.clear();
        }
        Deque<Long> times = hits.computeIfAbsent(bucket, k -> new ArrayDeque<>());
        synchronized (times) {
            long from = nowMillis - window.toMillis();
            while (!times.isEmpty() && times.peekFirst() <= from) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                return false;
            }
            times.addLast(nowMillis);
            return true;
        }
    }
}
