package com.oneblog.block;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 차단 (Crowfoot ERD blocks, SOC-05). 행이 단순해 JDBC로 다룬다. */
@Repository
public class BlockRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public BlockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(Long userId, Long blockedUserId) {
        if (userId == null || blockedUserId == null) {
            return false;
        }
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM blocks WHERE user_id = :userId AND blocked_user_id = :blocked", args(userId,
                        blockedUserId), Integer.class);
        return count != null && count > 0;
    }

    /** 둘 중 한 사람이라도 상대를 차단했는지. */
    public boolean eitherBlocked(Long a, Long b) {
        return exists(a, b) || exists(b, a);
    }

    /** 이미 있으면 0 (uk_blocks_user_id_blocked_user_id). */
    public int insert(Long userId, Long blockedUserId) {
        return jdbc.update("INSERT IGNORE INTO blocks (user_id, blocked_user_id) VALUES (:userId, :blocked)",
                args(userId, blockedUserId));
    }

    public int delete(Long userId, Long blockedUserId) {
        return jdbc.update("DELETE FROM blocks WHERE user_id = :userId AND blocked_user_id = :blocked",
                args(userId, blockedUserId));
    }

    public List<Long> blockedIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return jdbc.queryForList("SELECT blocked_user_id FROM blocks WHERE user_id = :userId",
                new MapSqlParameterSource("userId", userId), Long.class);
    }

    private static MapSqlParameterSource args(Long userId, Long blockedUserId) {
        return new MapSqlParameterSource().addValue("userId", userId).addValue("blocked", blockedUserId);
    }
}
