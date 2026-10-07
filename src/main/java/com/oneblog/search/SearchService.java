package com.oneblog.search;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.BlogQueryService;
import com.oneblog.blog.dto.BlogListItem;
import com.oneblog.common.web.PageParams;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.post.PostCardService;
import com.oneblog.post.dto.PostCard;
import com.oneblog.tag.TagPolicy;
import com.oneblog.tag.TagService;

/**
 * 통합 검색 (BRD-08, BLG-03, 6.1).
 * - 블로그: 이름·소개(FULLTEXT ngram) 또는 블로그 태그(일치)
 * - 글: 제목(FULLTEXT ngram), 글 태그(일치), 작성자 닉네임(일치). 분류바(target)로 하나만 고를 수 있다
 * - 정렬: 관련도(기본)·최신·인기(블로그는 멤버 수, 글은 좋아요 수, D-89)
 * - 제외: 전체 공개가 아닌 블로그, 숨기거나 폐쇄한 블로그, 숨김·삭제 글. 내가 차단한 회원의 글은 013에서 뺀다
 */
@Service
public class SearchService {

    static final String VISIBLE_BLOG = """
            b.visibility = 'PUBLIC' AND b.status <> 'CLOSED' AND b.deleted_at IS NULL AND b.is_hidden = 0""";
    static final String VISIBLE_POST = """
            p.post_type IN ('BLOG', 'BLOG_NOTICE') AND p.deleted_at IS NULL AND p.is_hidden = 0""";

    private static final String BLOG_FT = "MATCH(b.name, b.description) AGAINST(:ft IN BOOLEAN MODE)";
    private static final String BLOG_TAG = "EXISTS (SELECT 1 FROM blog_tags bt WHERE bt.blog_id = b.id AND bt.tag_id = :tagId)";
    private static final String POST_FT = "MATCH(p.title) AGAINST(:ft IN BOOLEAN MODE)";
    private static final String POST_TAG = "EXISTS (SELECT 1 FROM post_tags pt WHERE pt.post_id = p.id AND pt.tag_id = :tagId)";
    private static final String POST_AUTHOR = "(p.user_id = :authorId AND p.author_detached = 0)";

    private static final List<String> TARGETS = List.of("all", "title", "tag", "author");

    private final NamedParameterJdbcTemplate jdbc;
    private final TagPolicy tagPolicy;
    private final TagService tagService;
    private final UserRepository userRepository;
    private final BlogQueryService blogQueryService;
    private final PostCardService postCardService;
    private final RecentSearchService recentSearchService;
    private final List<SearchFilter> filters;

    public SearchService(NamedParameterJdbcTemplate jdbc, TagPolicy tagPolicy, TagService tagService,
            UserRepository userRepository, BlogQueryService blogQueryService, PostCardService postCardService,
            RecentSearchService recentSearchService, List<SearchFilter> filters) {
        this.jdbc = jdbc;
        this.tagPolicy = tagPolicy;
        this.tagService = tagService;
        this.userRepository = userRepository;
        this.blogQueryService = blogQueryService;
        this.postCardService = postCardService;
        this.recentSearchService = recentSearchService;
        this.filters = filters;
    }

    @Transactional
    public SearchPage<BlogListItem> blogs(String rawQ, String rawSort, PageParams params, Long viewerId) {
        SearchQuery query = SearchQuery.parse(rawQ);
        String sort = sort(rawSort);
        remember(viewerId, query, params);
        MapSqlParameterSource args = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        if (query.fulltext() != null) {
            args.addValue("ft", query.fulltext());
            conditions.add(BLOG_FT);
            scores.add(BLOG_FT);
        }
        Long tagId = tagId(query);
        if (tagId != null) {
            args.addValue("tagId", tagId);
            conditions.add(BLOG_TAG);
            scores.add("(CASE WHEN " + BLOG_TAG + " THEN 10 ELSE 0 END)");
        }
        if (conditions.isEmpty()) {
            return new SearchPage<>(query.text(), "blog", "all", sort, List.of(), 1, params.size(), 0, 1);
        }
        String fromWhere = "FROM blogs b WHERE " + VISIBLE_BLOG + " AND (" + String.join(" OR ", conditions) + ")";
        String orderBy = switch (sort) {
            case "latest" -> "b.created_at DESC, b.id DESC";
            case "popular" -> "b.member_count DESC, b.created_at DESC, b.id DESC";
            default -> "(" + String.join(" + ", scores) + ") DESC, b.created_at DESC, b.id DESC";
        };
        PagedIds.Result result = PagedIds.read(jdbc, "b.id", fromWhere, orderBy, args, params);
        return new SearchPage<>(query.text(), "blog", "all", sort, blogQueryService.toListItems(result.ids()),
                result.params().page(), result.params().size(), result.totalItems(), result.totalPages());
    }

    @Transactional
    public SearchPage<PostCard> posts(String rawQ, String rawTarget, String rawSort, PageParams params, Long viewerId) {
        SearchQuery query = SearchQuery.parse(rawQ);
        String sort = sort(rawSort);
        String target = TARGETS.contains(rawTarget) ? rawTarget : "all";
        remember(viewerId, query, params);
        MapSqlParameterSource args = new MapSqlParameterSource();
        List<String> conditions = new ArrayList<>();
        List<String> scores = new ArrayList<>();
        if (query.fulltext() != null && (target.equals("all") || target.equals("title"))) {
            args.addValue("ft", query.fulltext());
            conditions.add(POST_FT);
            scores.add(POST_FT);
        }
        Long tagId = target.equals("all") || target.equals("tag") ? tagId(query) : null;
        if (tagId != null) {
            args.addValue("tagId", tagId);
            conditions.add(POST_TAG);
            scores.add("(CASE WHEN " + POST_TAG + " THEN 10 ELSE 0 END)");
        }
        Long authorId = target.equals("all") || target.equals("author") ? authorId(query) : null;
        if (authorId != null) {
            args.addValue("authorId", authorId);
            conditions.add(POST_AUTHOR);
            scores.add("(CASE WHEN " + POST_AUTHOR + " THEN 10 ELSE 0 END)");
        }
        if (conditions.isEmpty()) {
            return new SearchPage<>(query.text(), "post", target, sort, List.of(), 1, params.size(), 0, 1);
        }
        StringBuilder fromWhere = new StringBuilder("FROM posts p JOIN blogs b ON b.id = p.blog_id WHERE ")
                .append(VISIBLE_POST).append(" AND ").append(VISIBLE_BLOG)
                .append(" AND (").append(String.join(" OR ", conditions)).append(")");
        appendFilters(fromWhere, args, viewerId);
        String orderBy = switch (sort) {
            case "latest" -> "p.created_at DESC, p.id DESC";
            case "popular" -> "p.like_count DESC, p.created_at DESC, p.id DESC";
            default -> "(" + String.join(" + ", scores) + ") DESC, p.created_at DESC, p.id DESC";
        };
        PagedIds.Result result = PagedIds.read(jdbc, "p.id", fromWhere.toString(), orderBy, args, params);
        return new SearchPage<>(query.text(), "post", target, sort, postCardService.cards(result.ids()),
                result.params().page(), result.params().size(), result.totalItems(), result.totalPages());
    }

    /** 다른 기능(차단 013)이 글 목록에서 뺄 조건을 더한다. */
    void appendFilters(StringBuilder fromWhere, MapSqlParameterSource args, Long viewerId) {
        for (SearchFilter filter : filters) {
            String condition = filter.postCondition(viewerId, args);
            if (condition != null) {
                fromWhere.append(" AND ").append(condition);
            }
        }
    }

    private void remember(Long viewerId, SearchQuery query, PageParams params) {
        // 첫 페이지를 볼 때만 남긴다 (페이지를 넘길 때마다 순서가 바뀌지 않게)
        if (viewerId != null && params.page() == 1) {
            recentSearchService.remember(viewerId, query.text());
        }
    }

    private Long tagId(SearchQuery query) {
        String tag = tagPolicy.normalize(query.text());
        return tagPolicy.isValid(tag) ? tagService.findTagId(tag) : null;
    }

    private Long authorId(SearchQuery query) {
        return userRepository.findByNickname(query.text())
                .filter(u -> u.isActive() && u.getRole() == UserRole.USER)
                .map(u -> u.getId()).orElse(null);
    }

    private static String sort(String raw) {
        return "latest".equals(raw) || "popular".equals(raw) ? raw : "relevance";
    }
}
