package com.oneblog.search;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.PageParams;
import com.oneblog.post.PostCardService;
import com.oneblog.post.dto.PostCard;

/**
 * 메인 피드 (BRD-09, D-65, D-73). 볼 때마다 모아온다(피드 테이블 없음).
 * - latest·popular: 전체 공개 블로그의 공개 글 (인기 = 좋아요 수, D-89). 누구나
 * - following: 내가 팔로우한 회원의 글. subscriptions: 내가 구독한 블로그의 글. 로그인한 회원만
 * - myblogs: 내가 참여 중인 블로그(만든 블로그 포함)의 새 글. 메인 "내 블로그" 탭 아래 (D-113). 정지 중인 블로그는 빠진다
 * - following·subscriptions도 내가 볼 수 있는 블로그의 글만: 전체 공개, 내가 멤버인 블로그,
 *   구독한 일부 공개 블로그(D-50). 비공개로 바뀐 블로그는 멤버가 아니면 빠진다 (D-37)
 */
@Service
public class FeedService {

    private static final List<String> TABS = List.of("latest", "popular", "following", "subscriptions", "myblogs");

    private static final String VIEWER_CAN_SEE = """
            b.status <> 'CLOSED' AND b.deleted_at IS NULL AND (
              (b.visibility = 'PUBLIC' AND b.is_hidden = 0)
              OR EXISTS (SELECT 1 FROM blog_members m
                         WHERE m.blog_id = b.id AND m.user_id = :viewerId AND m.status = 'ACTIVE')
              OR (b.visibility = 'UNLISTED' AND b.is_hidden = 0 AND EXISTS (
                    SELECT 1 FROM blog_subscriptions vs WHERE vs.blog_id = b.id AND vs.user_id = :viewerId)))""";

    private final NamedParameterJdbcTemplate jdbc;
    private final PostCardService postCardService;
    private final SearchService searchService;

    public FeedService(NamedParameterJdbcTemplate jdbc, PostCardService postCardService, SearchService searchService) {
        this.jdbc = jdbc;
        this.postCardService = postCardService;
        this.searchService = searchService;
    }

    @Transactional(readOnly = true)
    public SearchPage<PostCard> feed(String rawTab, PageParams params, Long viewerId) {
        String tab = rawTab != null && TABS.contains(rawTab) ? rawTab : "latest";
        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder fromWhere = new StringBuilder("FROM posts p JOIN blogs b ON b.id = p.blog_id WHERE ")
                .append(SearchService.VISIBLE_POST);
        switch (tab) {
            case "following" -> {
                requireLogin(viewerId);
                args.addValue("viewerId", viewerId);
                fromWhere.append(" AND ").append(VIEWER_CAN_SEE)
                        .append(" AND p.author_detached = 0")
                        .append(" AND p.user_id IN (SELECT f.followee_id FROM follows f WHERE f.follower_id = :viewerId)");
            }
            case "subscriptions" -> {
                requireLogin(viewerId);
                args.addValue("viewerId", viewerId);
                fromWhere.append(" AND ").append(VIEWER_CAN_SEE)
                        .append(" AND p.blog_id IN (SELECT s.blog_id FROM blog_subscriptions s WHERE s.user_id = :viewerId)");
            }
            case "myblogs" -> {
                requireLogin(viewerId);
                args.addValue("viewerId", viewerId);
                // 같은 블로그 멤버의 글이라 차단 여부와 관계없이 보인다 (D-36)
                fromWhere.append(" AND b.status <> 'CLOSED' AND b.deleted_at IS NULL")
                        .append(" AND p.blog_id IN (SELECT m.blog_id FROM blog_members m WHERE m.user_id = :viewerId")
                        .append(" AND m.status = 'ACTIVE'")
                        .append(" AND (m.suspended_until IS NULL OR m.suspended_until <= CURRENT_TIMESTAMP(6)))");
            }
            default -> fromWhere.append(" AND ").append(SearchService.VISIBLE_BLOG);
        }
        if (!"myblogs".equals(tab)) {
            searchService.appendFilters(fromWhere, args, viewerId);
        }
        String orderBy = "popular".equals(tab) ? "p.like_count DESC, p.created_at DESC, p.id DESC"
                : "p.created_at DESC, p.id DESC";
        PagedIds.Result result = PagedIds.read(jdbc, "p.id", fromWhere.toString(), orderBy, args, params);
        return new SearchPage<>(null, "feed", tab, "popular".equals(tab) ? "popular" : "latest",
                postCardService.cards(result.ids()), result.params().page(), result.params().size(),
                result.totalItems(), result.totalPages());
    }

    private static void requireLogin(Long viewerId) {
        if (viewerId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다.");
        }
    }
}
