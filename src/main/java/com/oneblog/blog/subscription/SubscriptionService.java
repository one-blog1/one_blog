package com.oneblog.blog.subscription;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogPolicy;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.Times;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;

/**
 * 블로그 구독·취소 (SOC-01, 6.6: 한 번만, 다시 누르면 취소).
 * - 구독은 그 블로그를 볼 수 있는 회원만. 일부 공개 블로그는 링크(key)로 들어와 구독하면 그 뒤로 링크 없이 들어올 수 있다 (D-50)
 * - 취소는 언제나 된다. 비공개로 바뀌어 더 볼 수 없는 블로그도 구독을 끊을 수 있어야 한다 (D-37)
 * - 관리자는 구독하지 않는다 (D-90)
 */
@Service
public class SubscriptionService {

    private final BlogSubscriptionRepository repository;
    private final BlogRepository blogRepository;
    private final BlogAccessService accessService;
    private final BlogPolicy blogPolicy;
    private final UserRepository userRepository;
    private final List<SubscriptionListener> listeners;

    public SubscriptionService(BlogSubscriptionRepository repository, BlogRepository blogRepository,
            BlogAccessService accessService, BlogPolicy blogPolicy, UserRepository userRepository,
            List<SubscriptionListener> listeners) {
        this.repository = repository;
        this.blogRepository = blogRepository;
        this.accessService = accessService;
        this.blogPolicy = blogPolicy;
        this.userRepository = userRepository;
        this.listeners = listeners;
    }

    public record SubscribeState(boolean subscribed, long subscriberCount) {
    }

    public record SubscribedBlog(String slug, String name, String coverImageUrl, String visibility, int memberCount,
            java.time.OffsetDateTime subscribedAt) {
    }

    @Transactional
    public SubscribeState toggle(String rawSlug, String key, AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 블로그를 구독할 수 없습니다.");
        }
        String slug = blogPolicy.normalizeSlug(rawSlug);
        Blog blog = slug == null ? null : blogRepository.findBySlug(slug).filter(Blog::isOpen).orElse(null);
        if (blog == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }
        userRepository.findForUpdateById(principal.id());
        var existing = repository.findByBlogIdAndUserId(blog.getId(), principal.id());
        if (existing.isPresent()) {
            repository.delete(existing.get());
            repository.flush();
            return new SubscribeState(false, repository.countByBlogId(blog.getId()));
        }
        accessService.check(blog, key, principal.id());
        try {
            repository.saveAndFlush(BlogSubscription.of(blog.getId(), principal.id()));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_SUBSCRIBED", "이미 구독한 블로그입니다.");
        }
        listeners.forEach(l -> l.subscribed(blog.getId(), principal.id()));
        return new SubscribeState(true, repository.countByBlogId(blog.getId()));
    }

    /** 내가 구독한 블로그 (최근 구독 순). 폐쇄·삭제된 블로그는 빠진다. */
    @Transactional(readOnly = true)
    public List<SubscribedBlog> mySubscriptions(Long userId) {
        List<SubscribedBlog> result = new ArrayList<>();
        for (Object[] row : blogRepository.findSubscribedRows(userId)) {
            Blog blog = (Blog) row[0];
            result.add(new SubscribedBlog(blog.getSlug(), blog.getName(), blog.getCoverImageUrl(),
                    blog.getVisibility().name(), blog.getMemberCount(), Times.toOffset((java.time.LocalDateTime) row[1])));
        }
        return result;
    }
}
