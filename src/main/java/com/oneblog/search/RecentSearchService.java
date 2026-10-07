package com.oneblog.search;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.web.Times;

/** 최근 검색어 (6.1): 본인만, 최대 10개, 하나씩 지우기·모두 지우기. 같은 검색어는 시각만 새로 한다. */
@Service
public class RecentSearchService {

    public static final int MAX = 10;

    private final NamedParameterJdbcTemplate jdbc;

    public RecentSearchService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RecentSearch(Long id, String keyword, OffsetDateTime searchedAt) {
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void remember(Long userId, String keyword) {
        MapSqlParameterSource args = new MapSqlParameterSource().addValue("userId", userId).addValue("keyword", keyword);
        jdbc.update("""
                INSERT INTO recent_searches (user_id, keyword, searched_at) VALUES (:userId, :keyword, CURRENT_TIMESTAMP(6))
                ON DUPLICATE KEY UPDATE searched_at = CURRENT_TIMESTAMP(6)
                """, args);
        // 최근 10개만 남긴다. MySQL은 같은 테이블을 하위 쿼리에서 바로 못 읽어 한 번 더 감싼다
        jdbc.update("""
                DELETE FROM recent_searches WHERE user_id = :userId AND id NOT IN (
                  SELECT id FROM (
                    SELECT id FROM recent_searches WHERE user_id = :userId ORDER BY searched_at DESC, id DESC LIMIT 10
                  ) keep_rows)
                """, args);
    }

    @Transactional(readOnly = true)
    public List<RecentSearch> list(Long userId) {
        return jdbc.query("""
                SELECT id, keyword, searched_at FROM recent_searches WHERE user_id = :userId
                ORDER BY searched_at DESC, id DESC LIMIT 10
                """, new MapSqlParameterSource("userId", userId),
                (rs, i) -> new RecentSearch(rs.getLong(1), rs.getString(2),
                        Times.toOffset(rs.getTimestamp(3).toLocalDateTime())));
    }

    /** 내 검색어만 지운다 (남의 ID를 넣어도 user_id 조건 때문에 지워지지 않는다). */
    @Transactional
    public void delete(Long userId, Long id) {
        jdbc.update("DELETE FROM recent_searches WHERE id = :id AND user_id = :userId",
                new MapSqlParameterSource().addValue("id", id).addValue("userId", userId));
    }

    @Transactional
    public void deleteAll(Long userId) {
        jdbc.update("DELETE FROM recent_searches WHERE user_id = :userId", new MapSqlParameterSource("userId", userId));
    }
}
