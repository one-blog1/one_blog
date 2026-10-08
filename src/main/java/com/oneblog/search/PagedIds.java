package com.oneblog.search;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.oneblog.common.web.PageParams;

/**
 * "FROM ... WHERE ..." 조각으로 개수와 한 페이지의 ID를 읽는다. 페이지가 범위를 넘으면 1페이지로 (D-76).
 * 조각은 코드에 적힌 고정 문자열만 쓰고, 사용자 값은 모두 바인딩 파라미터로 넘긴다 (constitution III).
 */
final class PagedIds {

    record Result(List<Long> ids, PageParams params, long totalItems, int totalPages) {
    }

    private PagedIds() {
    }

    static Result read(NamedParameterJdbcTemplate jdbc, String idColumn, String fromWhere, String orderBy,
            MapSqlParameterSource args, PageParams params) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, args, Long.class);
        long totalItems = total == null ? 0 : total;
        int totalPages = params.totalPages(totalItems);
        if (params.page() > totalPages) {
            params = params.firstPage();
        }
        MapSqlParameterSource pageArgs = new MapSqlParameterSource(args.getValues())
                .addValue("limit", params.size())
                .addValue("offset", (long) params.zeroBasedPage() * params.size());
        List<Long> ids = jdbc.queryForList("SELECT " + idColumn + " " + fromWhere + " ORDER BY " + orderBy
                + " LIMIT :limit OFFSET :offset", pageArgs, Long.class);
        return new Result(ids, params, totalItems, totalPages);
    }
}
