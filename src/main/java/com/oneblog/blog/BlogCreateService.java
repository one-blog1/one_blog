package com.oneblog.blog;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.dto.BlogQuotaResponse;
import com.oneblog.blog.dto.CreateBlogRequest;
import com.oneblog.blog.dto.CreateBlogResponse;
import com.oneblog.blog.dto.SlugAvailabilityResponse;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.file.FilePurpose;
import com.oneblog.file.StoredFile;
import com.oneblog.file.StoredFileRepository;
import com.oneblog.member.UserRepository;
import com.oneblog.member.UserRole;
import com.oneblog.member.ValidationFailedException;
import com.oneblog.tag.TagPolicy;
import com.oneblog.tag.TagService;

/**
 * 블로그 만들기와 생성 개수 제한 (BLG-01, BLG-10, research R2·R3·R5·R6).
 */
@Service
public class BlogCreateService {

    static final Set<BlogVisibility> PUBLIC_KIND = Set.of(BlogVisibility.PUBLIC, BlogVisibility.UNLISTED);
    static final Set<BlogVisibility> PRIVATE_KIND = Set.of(BlogVisibility.PRIVATE);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BlogRepository blogRepository;
    private final BlogMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StoredFileRepository fileRepository;
    private final BlogPolicy policy;
    private final TagPolicy tagPolicy;
    private final TagService tagService;
    private final BlogProperties properties;

    public BlogCreateService(BlogRepository blogRepository, BlogMemberRepository memberRepository,
            UserRepository userRepository, StoredFileRepository fileRepository, BlogPolicy policy,
            TagPolicy tagPolicy, TagService tagService, BlogProperties properties) {
        this.blogRepository = blogRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.policy = policy;
        this.tagPolicy = tagPolicy;
        this.tagService = tagService;
        this.properties = properties;
    }

    /** 주소를 쓸 수 있는지 (만들기 전 확인용). 최종 판단은 create에서 다시 한다. */
    @Transactional(readOnly = true)
    public SlugAvailabilityResponse checkSlug(String rawSlug) {
        String slug = policy.normalizeSlug(rawSlug);
        if (!policy.isValidSlugFormat(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "INVALID_FORMAT");
        }
        if (policy.isReservedSlug(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "RESERVED");
        }
        if (blogRepository.existsBySlug(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "TAKEN");
        }
        return SlugAvailabilityResponse.ok(slug);
    }

    /** 남은 생성 개수 (BLG-10). 공개는 공개 + 일부 공개 (D-51). */
    @Transactional(readOnly = true)
    public BlogQuotaResponse quota(AuthenticatedUser principal) {
        rejectAdmin(principal);
        BlogProperties.Limit limit = properties.limit();
        return new BlogQuotaResponse(
                BlogQuotaResponse.Quota.of(blogRepository.countOwned(principal.id(), PUBLIC_KIND), limit.publicLimit()),
                BlogQuotaResponse.Quota.of(blogRepository.countOwned(principal.id(), PRIVATE_KIND),
                        limit.privateLimit()));
    }

    @Transactional
    public CreateBlogResponse create(AuthenticatedUser principal, CreateBlogRequest request) {
        rejectAdmin(principal);

        // 1. 입력 정리와 규칙 검사
        String name = policy.normalizeName(request.name());
        String description = policy.normalizeDescription(request.description());
        List<ErrorResponse.FieldError> errors = new ArrayList<>();
        if (!policy.isValidName(name)) {
            errors.add(new ErrorResponse.FieldError("name", "블로그 이름은 1~50자로 입력해 주세요."));
        }
        if (!policy.isValidDescription(description)) {
            errors.add(new ErrorResponse.FieldError("description", "소개는 500자까지 쓸 수 있습니다."));
        }
        if (request.visibility() == null) {
            errors.add(new ErrorResponse.FieldError("visibility", "공개 범위를 골라 주세요."));
        }
        if (request.joinPolicy() == null) {
            errors.add(new ErrorResponse.FieldError("joinPolicy", "참여 방식을 골라 주세요."));
        }
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        List<String> tags = tagPolicy.normalizeAll(request.tags(), "tags");

        String slug = policy.normalizeSlug(request.slug());
        if (!policy.isValidSlugFormat(slug)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG",
                    "블로그 주소는 영문 소문자, 숫자, -로 된 3~30자로 입력해 주세요.");
        }
        if (policy.isReservedSlug(slug)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RESERVED_SLUG", "사용할 수 없는 블로그 주소입니다.");
        }

        // 2. 회원 행을 잠그고 개수를 센다 — 같은 회원의 동시 요청은 여기서 차례로 처리된다 (D-68)
        userRepository.findForUpdateById(principal.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."));
        checkLimit(principal.id(), request.visibility());

        // 3. 주소 중복, 대표 이미지
        if (blogRepository.existsBySlug(slug)) {
            throw slugTaken();
        }
        String coverImageUrl = resolveCover(principal.id(), request.coverFileId());

        // 4. 저장: 블로그 → 블로그장 멤버십 → 태그
        String shareToken = request.visibility() == BlogVisibility.UNLISTED ? newShareToken() : null;
        Blog blog;
        try {
            blog = blogRepository.saveAndFlush(Blog.create(slug, name, description, coverImageUrl,
                    request.visibility(), request.joinPolicy(), shareToken));
        } catch (DataIntegrityViolationException e) {
            // 다른 회원이 같은 주소로 동시에 만든 경우 (uk_blogs_slug)
            throw slugTaken();
        }
        memberRepository.save(BlogMember.owner(blog.getId(), principal.id()));
        tagService.attachToBlog(blog.getId(), tags);

        return new CreateBlogResponse(blog.getId(), slug, BlogUrls.blogUrl(slug), BlogUrls.shareUrl(blog));
    }

    private void checkLimit(Long userId, BlogVisibility visibility) {
        BlogProperties.Limit limit = properties.limit();
        if (visibility == BlogVisibility.PRIVATE) {
            if (blogRepository.countOwned(userId, PRIVATE_KIND) >= limit.privateLimit()) {
                throw ApiException.withLimit(HttpStatus.CONFLICT, "BLOG_LIMIT_EXCEEDED",
                        "비공개 블로그는 " + limit.privateLimit() + "개까지 만들 수 있습니다.", "PRIVATE",
                        limit.privateLimit());
            }
        } else if (blogRepository.countOwned(userId, PUBLIC_KIND) >= limit.publicLimit()) {
            throw ApiException.withLimit(HttpStatus.CONFLICT, "BLOG_LIMIT_EXCEEDED",
                    "공개 블로그(일부 공개 포함)는 " + limit.publicLimit() + "개까지 만들 수 있습니다.", "PUBLIC",
                    limit.publicLimit());
        }
    }

    /** 내가 올린, 삭제되지 않은 대표 이미지여야 한다. */
    private String resolveCover(Long userId, Long coverFileId) {
        if (coverFileId == null) {
            return null;
        }
        StoredFile file = fileRepository.findById(coverFileId).orElse(null);
        if (file == null || file.isDeleted() || !userId.equals(file.getUserId())
                || file.getPurpose() != FilePurpose.BLOG_COVER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COVER_FILE", "대표 이미지를 다시 올려 주세요.");
        }
        return file.url();
    }

    private static void rejectAdmin(AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 블로그 활동을 할 수 없습니다.");
        }
    }

    private static ApiException slugTaken() {
        return new ApiException(HttpStatus.CONFLICT, "SLUG_TAKEN", "이미 쓰이는 블로그 주소입니다.");
    }

    /** 128비트 무작위 값, 소문자 16진수 32자 (research R6). */
    static String newShareToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
