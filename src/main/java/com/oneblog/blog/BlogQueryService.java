package com.oneblog.blog;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.dto.BlogDetailResponse;
import com.oneblog.blog.dto.BlogListItem;
import com.oneblog.blog.dto.BlogPageResponse;
import com.oneblog.blog.dto.MyBlogsResponse;
import com.oneblog.blog.subscription.BlogSubscriptionRepository;
import com.oneblog.tag.TagService;

/**
 * 블로그 목록·내 블로그·첫 화면 정보 (BLG-02, BLG-06, research R8).
 * 목록 한 번에 쿼리는 목록·개수·블로그장·태그 4번으로 고정한다 (블로그마다 따로 조회하지 않음).
 */
@Service
public class BlogQueryService {

    public static final int DEFAULT_SIZE = 10;
    private static final List<Integer> SIZES = List.of(10, 20, 30);

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final TagService tagService;
    private final BlogAccessService accessService;
    private final BlogSubscriptionRepository subscriptionRepository;

    public BlogQueryService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
            TagService tagService, BlogAccessService accessService,
            BlogSubscriptionRepository subscriptionRepository) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.tagService = tagService;
        this.accessService = accessService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** 메인 목록. 정렬·개수·페이지가 규칙 밖이면 기본값으로 바꾸고 실제 값을 응답에 넣는다. */
    @Transactional(readOnly = true)
    public BlogPageResponse list(String rawSort, Integer rawPage, Integer rawSize) {
        String sort = "popular".equals(rawSort) ? "popular" : "latest";
        int size = rawSize != null && SIZES.contains(rawSize) ? rawSize : DEFAULT_SIZE;
        Sort order = "popular".equals(sort)
                ? Sort.by(Sort.Order.desc("memberCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

        int page = rawPage == null || rawPage < 1 ? 1 : rawPage;
        Page<Blog> result = blogRepository.findPublicList(PageRequest.of(page - 1, size, order));
        if (page > 1 && page > result.getTotalPages()) {
            page = 1;
            result = blogRepository.findPublicList(PageRequest.of(0, size, order));
        }

        List<BlogListItem> items = buildItems(result.getContent());
        return new BlogPageResponse(items, sort, page, size, result.getTotalElements(),
                Math.max(1, result.getTotalPages()));
    }

    /** 블로그 ID 순서대로 목록 한 줄을 만든다 (통합 검색 010). 보여도 되는지는 부르는 쪽이 골랐어야 한다. */
    @Transactional(readOnly = true)
    public List<BlogListItem> toListItems(List<Long> blogIds) {
        if (blogIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Blog> byId = new HashMap<>();
        blogRepository.findAllById(blogIds).forEach(b -> byId.put(b.getId(), b));
        return buildItems(blogIds.stream().map(byId::get).filter(Objects::nonNull).toList());
    }

    private List<BlogListItem> buildItems(List<Blog> blogs) {
        List<Long> ids = blogs.stream().map(Blog::getId).toList();
        Map<Long, String> owners = ownerNicknames(ids);
        Map<Long, List<String>> tags = tagService.findBlogTags(ids);
        return blogs.stream()
                .map(b -> new BlogListItem(b.getSlug(), b.getName(), b.getDescription(), b.getCoverImageUrl(),
                        tags.getOrDefault(b.getId(), List.of()), b.getMemberCount(), owners.get(b.getId()),
                        toOffset(b.getCreatedAt())))
                .toList();
    }

    /** 내 블로그: 블로그장인 블로그와 참여한 블로그를 나눈다 (BLG-06). 공개 범위와 상관없이 보인다. */
    @Transactional(readOnly = true)
    public MyBlogsResponse myBlogs(Long userId) {
        List<MyBlogsResponse.Item> owned = new ArrayList<>();
        List<MyBlogsResponse.Item> joined = new ArrayList<>();
        for (Object[] row : memberRepository.findMyBlogs(userId)) {
            Blog blog = (Blog) row[0];
            BlogRole role = (BlogRole) row[1];
            MyBlogsResponse.Item item = new MyBlogsResponse.Item(blog.getSlug(), blog.getName(),
                    blog.getCoverImageUrl(), blog.getVisibility(), role, blog.getMemberCount(),
                    toOffset(blog.getCreatedAt()));
            if (role == BlogRole.OWNER) {
                owned.add(item);
            } else {
                joined.add(item);
            }
        }
        return new MyBlogsResponse(owned, joined);
    }

    /** 블로그 첫 화면. 볼 수 없으면 BlogAccessService가 403·404를 던진다. */
    @Transactional(readOnly = true)
    public BlogDetailResponse detail(String slug, String key, Long viewerId) {
        BlogAccessService.Access access = accessService.check(slug, key, viewerId);
        Blog blog = access.blog();
        String shareUrl = access.isMember() ? BlogUrls.shareUrl(blog) : null;
        return new BlogDetailResponse(blog.getSlug(), blog.getName(), blog.getDescription(),
                blog.getCoverImageUrl(), tagService.findBlogTags(blog.getId()), blog.getVisibility(),
                blog.getJoinPolicy(), blog.getMemberCount(), ownerNicknames(List.of(blog.getId())).get(blog.getId()),
                toOffset(blog.getCreatedAt()), access.myRole(), shareUrl, permissions(blog, viewerId),
                viewerId != null && subscriptionRepository.existsByBlogIdAndUserId(blog.getId(), viewerId),
                subscriptionRepository.countByBlogId(blog.getId()), blog.getStatus(),
                blog.getCloseScheduledAt() == null ? null : toOffset(blog.getCloseScheduledAt()),
                access.adminView() ? adminViewReason(blog) : null);
    }

    /** 관리자 열람 안내 문구 (D-106). */
    public static String adminViewReason(Blog blog) {
        if (blog.isHidden()) {
            return "관리자가 숨긴 블로그예요.";
        }
        return switch (blog.getVisibility()) {
            case PRIVATE -> "비공개 블로그예요.";
            case UNLISTED -> "일부 공개 블로그예요.";
            default -> null;
        };
    }

    private BlogDetailResponse.Permissions permissions(Blog blog, Long viewerId) {
        BlogMember member = accessService.activeMembership(blog.getId(), viewerId);
        if (member == null) {
            return null;
        }
        return new BlogDetailResponse.Permissions(member.canEditInfo(), member.canManageMembers(),
                member.canManagePosts());
    }

    private Map<Long, String> ownerNicknames(List<Long> blogIds) {
        Map<Long, String> result = new HashMap<>();
        if (blogIds.isEmpty()) {
            return result;
        }
        for (Object[] row : memberRepository.findOwnerNicknames(blogIds)) {
            result.put((Long) row[0], (String) row[1]);
        }
        return result;
    }

    private static OffsetDateTime toOffset(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
