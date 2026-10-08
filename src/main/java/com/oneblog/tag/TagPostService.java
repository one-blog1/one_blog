package com.oneblog.tag;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.web.PageParams;
import com.oneblog.post.PostCardService;
import com.oneblog.post.dto.PostCardPage;
import com.oneblog.search.SearchFilter;

/**
 * 같은 태그의 글 (BRD-04). 태그 링크는 누구나 열 수 있으므로, 전체 공개 블로그의 보이는 글만 보여준다
 * (6.1과 같은 기준: 일부 공개·비공개 블로그, 숨김·삭제 글, 폐쇄·숨긴 블로그는 빠진다).
 */
@Service
public class TagPostService {

    private static final String FROM = """
            FROM post_tags pt
            JOIN posts p ON p.id = pt.post_id
            JOIN blogs b ON b.id = p.blog_id
            WHERE pt.tag_id = :tagId
              AND p.post_type IN ('BLOG', 'BLOG_NOTICE') AND p.deleted_at IS NULL AND p.is_hidden = 0
              AND b.visibility = 'PUBLIC' AND b.status <> 'CLOSED' AND b.deleted_at IS NULL AND b.is_hidden = 0
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final TagPolicy tagPolicy;
    private final TagService tagService;
    private final PostCardService cardService;
    private final List<SearchFilter> filters;

    public TagPostService(NamedParameterJdbcTemplate jdbc, TagPolicy tagPolicy, TagService tagService,
            PostCardService cardService, List<SearchFilter> filters) {
        this.jdbc = jdbc;
        this.tagPolicy = tagPolicy;
        this.tagService = tagService;
        this.cardService = cardService;
        this.filters = filters;
    }

    @Transactional(readOnly = true)
    public PostCardPage posts(String rawTag, Long viewerId, PageParams params) {
        String tag = tagPolicy.normalize(rawTag);
        Long tagId = tagPolicy.isValid(tag) ? tagService.findTagId(tag) : null;
        if (tagId == null) {
            return new PostCardPage(List.of(), 1, params.size(), 0, 1);
        }
        MapSqlParameterSource args = new MapSqlParameterSource("tagId", tagId);
        // 차단한 회원의 글을 뺀다 (013, 검색과 같은 조건)
        StringBuilder from = new StringBuilder(FROM);
        for (SearchFilter filter : filters) {
            String condition = filter.postCondition(viewerId, args);
            if (condition != null) {
                from.append(" AND ").append(condition);
            }
        }
        String fromWhere = from.toString();
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromWhere, args, Long.class);
        long totalItems = total == null ? 0 : total;
        int totalPages = params.totalPages(totalItems);
        if (params.page() > totalPages) {
            params = params.firstPage();
        }
        args.addValue("limit", params.size()).addValue("offset", (long) params.zeroBasedPage() * params.size());
        List<Long> ids = jdbc.queryForList("SELECT p.id " + fromWhere
                + " ORDER BY p.created_at DESC, p.id DESC LIMIT :limit OFFSET :offset", args, Long.class);
        return new PostCardPage(cardService.cards(ids), params.page(), params.size(), totalItems, totalPages);
    }
}
