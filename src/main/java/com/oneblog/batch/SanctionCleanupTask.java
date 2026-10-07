package com.oneblog.batch;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 04:00 배치의 제재·신고 정리 (4.5).
 * - 내용을 지운 폐쇄 블로그(D-86)의 블랙리스트와 해제 문의를 지운다
 * - 1년 지난 신고·제재 기록을 지운다 (경고 3회·정지 3회는 1년 안의 기록으로 센다)
 */
@Component
public class SanctionCleanupTask implements DailyTask {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public SanctionCleanupTask(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public int order() {
        return 35;
    }

    @Override
    public String name() {
        return "블랙리스트·신고·제재 정리";
    }

    @Override
    public int run(LocalDateTime now) {
        MapSqlParameterSource args = new MapSqlParameterSource("yearAgo", now.minusYears(1));
        Integer count = tx.execute(status -> {
            int total = 0;
            total += jdbc.update("""
                    DELETE i FROM blacklist_inquiries i JOIN blogs b ON b.id = i.blog_id WHERE b.deleted_at IS NOT NULL
                    """, args);
            total += jdbc.update("""
                    DELETE l FROM blog_blacklists l JOIN blogs b ON b.id = l.blog_id WHERE b.deleted_at IS NOT NULL
                    """, args);
            total += jdbc.update("DELETE FROM sanctions WHERE created_at < :yearAgo", args);
            total += jdbc.update("DELETE FROM reports WHERE created_at < :yearAgo", args);
            return total;
        });
        return count == null ? 0 : count;
    }
}
