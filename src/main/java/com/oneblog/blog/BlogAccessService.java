package com.oneblog.blog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.common.web.ApiException;

/**
 * 블로그를 볼 수 있는지 한 곳에서 판단한다 (BLG-01, SEC-07, research R7).
 * 역할은 화면이나 토큰이 아니라 요청마다 저장된 멤버십으로 확인한다 (constitution III).
 * 정지·블랙리스트(013), 관리자 숨김(007)은 해당 기능에서 여기에 조건을 더한다.
 */
@Service
public class BlogAccessService {

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final BlogPolicy policy;

    public BlogAccessService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
            BlogPolicy policy) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.policy = policy;
    }

    /** 볼 수 있으면 블로그와 내 역할(멤버가 아니면 null), 없으면 404, 볼 수 없으면 403. 403·404에는 블로그 정보를 싣지 않는다. */
    @Transactional(readOnly = true)
    public Access check(String rawSlug, String key, Long viewerId) {
        String slug = policy.normalizeSlug(rawSlug);
        Blog blog = (slug == null || !policy.isValidSlugFormat(slug)) ? null
                : blogRepository.findBySlug(slug).orElse(null);
        return check(blog, key, viewerId);
    }

    /** 이미 찾은 블로그로 판단한다 (글 상세처럼 블로그 주소 대신 글 번호로 들어올 때). */
    @Transactional(readOnly = true)
    public Access check(Blog blog, String key, Long viewerId) {
        if (blog == null || !blog.isOpen()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }

        BlogRole myRole = null;
        if (viewerId != null) {
            myRole = memberRepository.findActive(blog.getId(), viewerId).map(BlogMember::getRole).orElse(null);
        }
        if (myRole != null) {
            return new Access(blog, myRole);
        }
        if (blog.isHidden()) {
            // 관리자가 숨긴 블로그는 멤버가 아니면 없는 블로그처럼 보인다 (ADM-02)
            throw new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다.");
        }

        return switch (blog.getVisibility()) {
            case PUBLIC -> new Access(blog, null);
            case UNLISTED -> {
                if (matches(blog.getShareToken(), key)) {
                    yield new Access(blog, null);
                }
                throw new ApiException(HttpStatus.FORBIDDEN, "LINK_REQUIRED", "링크가 있어야 볼 수 있는 블로그입니다.");
            }
            case PRIVATE -> throw new ApiException(HttpStatus.FORBIDDEN, "PRIVATE_BLOG", "비공개 블로그입니다.");
        };
    }

    /** 길이와 내용을 함께, 시간 차이 없이 비교한다 (research R6). */
    private static boolean matches(String expected, String given) {
        if (expected == null || given == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }

    /** 지금 활성 멤버십 (없으면 null). 글쓰기·관리 권한 확인에 쓴다. */
    @Transactional(readOnly = true)
    public BlogMember activeMembership(Long blogId, Long userId) {
        if (userId == null) {
            return null;
        }
        return memberRepository.findActive(blogId, userId).orElse(null);
    }

    public record Access(Blog blog, BlogRole myRole) {

        public boolean isMember() {
            return myRole != null;
        }
    }
}
