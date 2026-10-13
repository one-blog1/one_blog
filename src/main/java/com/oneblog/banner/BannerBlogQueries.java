package com.oneblog.banner;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.oneblog.search.SearchService;

/** 배너의 새 블로그·인기 블로그 후보 (6.8, D-133). 메인 블로그 목록과 같은 범위(전체 공개, 숨김·폐쇄 제외). */
@Component
class BannerBlogQueries {

    private final NamedParameterJdbcTemplate jdbc;

    BannerBlogQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 최근 days일에 만든 블로그, 최신순. */
    List<Long> newBlogs(int count, int days) {
        if (count <= 0) {
            return List.of();
        }
        return jdbc.queryForList("SELECT b.id FROM blogs b WHERE " + SearchService.VISIBLE_BLOG
                + " AND b.created_at >= :since ORDER BY b.created_at DESC, b.id DESC LIMIT :limit",
                args(count, days), Long.class);
    }

    /** 최근 days일에 새로 참여한 멤버(블로그장 제외)가 많은 블로그. 같으면 전체 멤버 수가 많은 순. */
    List<Long> popularBlogs(int count, int days) {
        if (count <= 0) {
            return List.of();
        }
        return jdbc.queryForList("SELECT b.id FROM blogs b"
                + " JOIN blog_members m ON m.blog_id = b.id AND m.status = 'ACTIVE' AND m.role <> 'OWNER'"
                + " AND m.joined_at >= :since"
                + " WHERE " + SearchService.VISIBLE_BLOG
                + " GROUP BY b.id, b.member_count"
                + " ORDER BY COUNT(*) DESC, b.member_count DESC, b.id DESC LIMIT :limit", args(count, days), Long.class);
    }

    private static MapSqlParameterSource args(int count, int days) {
        return new MapSqlParameterSource().addValue("since", LocalDateTime.now().minusDays(days)).addValue("limit", count);
    }
}
