package com.oneblog.batch;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 서버가 여러 대여도 예약 작업이 한 번만 돌게 하는 DB 잠금 (SCL-03). ERD의 `shedlock` 테이블을 ShedLock과 같은 방식으로 쓴다:
 * 잠금 끝 시각(lock_until)이 지난 행만 가져갈 수 있다. 라이브러리 대신 직접 둔 이유는 plan.md R4.
 */
@Component
public class SchedulerLock {

    private final NamedParameterJdbcTemplate jdbc;
    private final String owner;

    @Autowired
    public SchedulerLock(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        // 운영 컨테이너는 호스트 네트워크라 호스트 이름이 같고 java가 모두 PID 1이다. 앱마다 다른 이름이 되도록
        // 뜰 때 정한 무작위 값을 붙인다. 그래야 "잡은 쪽만 푼다"가 지켜진다 (029 FR-010)
        this.owner = host + ":" + ProcessHandle.current().pid() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 잠금 주인 이름을 정해 만든다. 테스트에서 서버 두 대를 흉내 낼 때 쓴다 (029 FR-010). */
    SchedulerLock(NamedParameterJdbcTemplate jdbc, String owner) {
        this.jdbc = jdbc;
        this.owner = owner;
    }

    /** 잠금을 얻으면 true. 얻은 쪽만 작업하고 끝나면 release. */
    public boolean tryLock(String name, Duration atMost) {
        Instant now = Instant.now();
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("until", Timestamp.from(now.plus(atMost)))
                .addValue("now", Timestamp.from(now))
                .addValue("by", owner);
        int updated = jdbc.update("""
                UPDATE shedlock SET lock_until = :until, locked_at = :now, locked_by = :by
                WHERE name = :name AND lock_until <= :now
                """, args);
        if (updated > 0) {
            return true;
        }
        try {
            return jdbc.update("""
                    INSERT INTO shedlock (name, lock_until, locked_at, locked_by) VALUES (:name, :until, :now, :by)
                    """, args) > 0;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public void release(String name) {
        release(name, Instant.now());
    }

    /**
     * holdUntil까지는 잠금을 남겨 두고 푼다 (이미 지났으면 바로). 일을 금방 끝낸 서버가 바로 풀면, 조금 늦게 온
     * 다른 서버가 같은 일을 한 번 더 할 수 있다. 그래서 일정 시간은 잠가 둔다 (ShedLock의 lockAtLeastFor, 029 FR-010).
     */
    public void release(String name, Instant holdUntil) {
        Instant now = Instant.now();
        Instant until = holdUntil.isAfter(now) ? holdUntil : now;
        jdbc.update("UPDATE shedlock SET lock_until = :until WHERE name = :name AND locked_by = :by",
                new MapSqlParameterSource().addValue("name", name).addValue("until", Timestamp.from(until))
                        .addValue("by", owner));
    }
}
