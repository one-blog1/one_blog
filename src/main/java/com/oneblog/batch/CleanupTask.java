package com.oneblog.batch;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 04:00 배치의 정리 단계.
 * - 만료·폐기된 Refresh Token (D-83), 만료·사용한 인증번호 (4.5 예외)
 * - 보관 기간(30일 또는 7일)이 지난 알림 (4.5, 6.7)
 * - 1년 지난 관리자 활동 기록 (4.5 예외). 신고·제재 기록은 013에서 더한다
 */
@Component
public class CleanupTask implements DailyTask {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public CleanupTask(NamedParameterJdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public String name() {
        return "만료 데이터 정리";
    }

    @Override
    public int run(LocalDateTime now) {
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("now", now)
                .addValue("yearAgo", now.minusYears(1));
        Integer count = tx.execute(status -> {
            int total = 0;
            total += jdbc.update("DELETE FROM refresh_tokens WHERE expires_at <= :now OR revoked_at IS NOT NULL", args);
            total += jdbc.update("DELETE FROM verification_codes WHERE expires_at <= :now OR used_at IS NOT NULL", args);
            total += jdbc.update("""
                    DELETE n FROM notifications n JOIN users u ON u.id = n.user_id
                    WHERE n.created_at < DATE_SUB(:now, INTERVAL u.notification_retention_days DAY)
                    """, args);
            total += jdbc.update("DELETE FROM admin_actions WHERE created_at < :yearAgo", args);
            return total;
        });
        return count == null ? 0 : count;
    }
}
