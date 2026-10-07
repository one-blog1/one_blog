package com.oneblog.batch;

import java.net.InetAddress;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

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

    public SchedulerLock(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        this.owner = host + ":" + ProcessHandle.current().pid();
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
        jdbc.update("UPDATE shedlock SET lock_until = :now WHERE name = :name AND locked_by = :by",
                new MapSqlParameterSource().addValue("name", name).addValue("now", Timestamp.from(Instant.now()))
                        .addValue("by", owner));
    }
}
