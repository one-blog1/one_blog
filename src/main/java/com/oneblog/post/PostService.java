package com.oneblog.post;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.admin.AdminActionLogger;
import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogQueryService;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.text.MarkdownRenderer;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.PageParams;
import com.oneblog.common.web.Times;
import com.oneblog.member.HiddenAuthors;
import com.oneblog.member.UserDisplayService;
import com.oneblog.member.UserRole;
import com.oneblog.post.dto.PostDetailResponse;
import com.oneblog.post.dto.PostListItem;
import com.oneblog.post.dto.PostPageResponse;
import com.oneblog.post.dto.PostRequest;

/**
 * 블로그 글 쓰기·보기·고치기·지우기와 목록 (BRD-01, BRD-02, 6.6).
 * - 쓰기: 그 블로그의 활성 멤버. 블로그 공지는 글 관리 권한(블로그장, 권한 받은 부블로그장)
 * - 고치기: 작성자 본인만 (D-07). 블로그를 떠난 작성자는 고칠 수 없다
 * - 지우기: 작성자 본인, 글 관리 권한이 있는 블로그장·부블로그장 (D-58). 관리자는 007
 * - 보기: 블로그를 볼 수 있는 사람 (BlogAccessService)
 * 역할은 요청마다 DB 멤버십으로 확인한다 (SEC-07).
 */
@Service
public class PostService {

    static final int NOTICE_LIMIT = 5;

    private final PostRepository postRepository;
    private final BlogRepository blogRepository;
    private final BlogAccessService accessService;
    private final PostPolicy policy;
    private final MarkdownRenderer markdown;
    private final UserDisplayService userDisplay;
    private final List<PostExtension> extensions;
    private final HiddenAuthors hiddenAuthorsOf;
    private final AdminActionLogger adminActionLogger;

    public PostService(PostRepository postRepository, BlogRepository blogRepository, BlogAccessService accessService,
            PostPolicy policy, MarkdownRenderer markdown, UserDisplayService userDisplay,
            List<PostExtension> extensions, HiddenAuthors hiddenAuthorsOf,
            AdminActionLogger adminActionLogger) {
        this.adminActionLogger = adminActionLogger;
        this.postRepository = postRepository;
        this.blogRepository = blogRepository;
        this.accessService = accessService;
        this.policy = policy;
        this.markdown = markdown;
        this.userDisplay = userDisplay;
        this.extensions = extensions;
        this.hiddenAuthorsOf = hiddenAuthorsOf;
    }

    @Transactional
    public Post create(String slug, String key, AuthenticatedUser principal, PostRequest request) {
        rejectAdmin(principal);
        Blog blog = accessService.check(slug, key, principal.id()).blog();
        BlogMember member = accessService.activeMembership(blog.getId(), principal.id());
        if (member == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "이 블로그의 멤버만 글을 쓸 수 있습니다.");
        }
        boolean notice = Boolean.TRUE.equals(request.notice());
        if (notice && !member.canManagePosts()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "블로그 공지는 글 관리 권한이 있어야 쓸 수 있습니다.");
        }
        String title = policy.normalizeTitle(request.title());
        policy.validate(title, request.content());
        for (PostExtension extension : extensions) {
            extension.validate(blog, request);
        }
        Post post = postRepository.saveAndFlush(Post.create(blog.getId(), principal.id(),
                notice ? PostType.BLOG_NOTICE : PostType.BLOG, title, request.content()));
        for (PostExtension extension : extensions) {
            extension.afterSave(blog, post, request, principal);
        }
        return post;
    }

    @Transactional
    public Post update(Long postId, AuthenticatedUser principal, PostRequest request) {
        Post post = findBlogPost(postId);
        Blog blog = blogOf(post);
        accessService.check(blog, null, principal.id());
        if (!isActiveAuthor(post, blog, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "글은 작성자만 고칠 수 있습니다.");
        }
        String title = policy.normalizeTitle(request.title());
        policy.validate(title, request.content());
        for (PostExtension extension : extensions) {
            extension.validate(blog, request);
        }
        post.edit(title, request.content());
        postRepository.flush();
        for (PostExtension extension : extensions) {
            extension.afterSave(blog, post, request, principal);
        }
        return post;
    }

    @Transactional
    public void delete(Long postId, AuthenticatedUser principal) {
        Post post = findBlogPost(postId);
        Blog blog = blogOf(post);
        if (principal.role() == UserRole.ADMIN) {
            // 관리자 삭제는 기록을 남겨야 해서 관리자 API(007)로만 한다
            throw new ApiException(HttpStatus.FORBIDDEN, "USE_ADMIN_API", "관리자 화면에서 삭제해 주세요.");
        }
        accessService.check(blog, null, principal.id());
        if (isActiveAuthor(post, blog, principal)) {
            post.delete(DeletedBy.AUTHOR);
        } else {
            BlogMember member = accessService.activeMembership(blog.getId(), principal.id());
            if (member == null || !member.canManagePosts()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 글을 지울 권한이 없습니다.");
            }
            post.delete(DeletedBy.BLOG_OWNER);
        }
        for (PostExtension extension : extensions) {
            extension.afterDelete(blog, post, principal);
        }
    }

    @Transactional(readOnly = true)
    public PostDetailResponse detail(Long postId, String key, AuthenticatedUser principal) {
        Post post = findBlogPostFor(postId, principal);
        Blog blog = blogOf(post);
        Long viewerId = principal == null ? null : principal.id();
        BlogAccessService.Access access = accessService.check(blog, key, viewerId);
        boolean author = principal != null && isActiveAuthor(post, blog, principal);
        BlogMember member = accessService.activeMembership(blog.getId(), viewerId);
        boolean admin = principal != null && principal.role() == UserRole.ADMIN;
        boolean canDelete = author || (member != null && member.canManagePosts());
        Map<Long, String> names = userDisplay.names(List.of(post.getUserId()));
        PostView view = new PostView();
        for (PostExtension extension : extensions) {
            extension.describe(blog, post, principal, view);
        }
        return new PostDetailResponse(post.getId(), blog.getSlug(), blog.getName(), post.getTitle(),
                markdown.toSafeHtml(post.getContent()), author ? post.getContent() : null, post.getUserId(),
                UserDisplayService.authorName(names, post.getUserId(), post.isAuthorDetached()), post.isNotice(),
                post.getCategoryId(), view.categoryName, view.tags, post.getViewCount() + view.extraViews,
                post.getLikeCount(), post.getCommentCount(), view.liked, Times.toOffset(post.getCreatedAt()),
                Times.toOffset(post.getUpdatedAt()), author, canDelete && !admin, principal != null && !admin,
                post.isHidden() ? "관리자가 숨긴 글이에요." : (access.adminView()
                        ? BlogQueryService.adminViewReason(blog) : null));
    }

    /** 블로그 글 목록: 공지(최근 5개)와 일반 글(최신순, 번호 페이지) (BRD-02, D-06, D-76). */
    @Transactional(readOnly = true)
    public PostPageResponse list(String slug, String key, Long viewerId, PageParams params, Long categoryId) {
        BlogAccessService.Access access = accessService.check(slug, key, viewerId);
        Blog blog = access.blog();
        Sort latest = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        List<Post> notices = postRepository.findVisibleInBlog(blog.getId(), List.of(PostType.BLOG_NOTICE),
                PageRequest.of(0, NOTICE_LIMIT, latest)).getContent();

        // 내가 차단한 회원의 글은 가린다. 이 블로그의 멤버면 그대로 보인다 (SOC-05, D-36)
        Set<Long> hidden = access.isMember() ? Set.of() : hiddenAuthorsOf.of(viewerId);
        Page<Post> page = fetch(blog, categoryId, params, latest, hidden);
        if (params.page() > 1 && params.page() > page.getTotalPages()) {
            params = params.firstPage();
            page = fetch(blog, categoryId, params, latest, hidden);
        }

        List<Post> all = new ArrayList<>(notices);
        all.addAll(page.getContent());
        Map<Long, String> names = userDisplay.names(authorIds(all));
        PostListContext context = new PostListContext(all);
        for (PostExtension extension : extensions) {
            extension.describeList(blog, context);
        }
        return new PostPageResponse(toItems(notices, names, context), toItems(page.getContent(), names, context),
                params.page(), params.size(), page.getTotalElements(), Math.max(1, page.getTotalPages()));
    }

    private Page<Post> fetch(Blog blog, Long categoryId, PageParams params, Sort sort, Set<Long> hidden) {
        PageRequest request = PageRequest.of(params.zeroBasedPage(), params.size(), sort);
        if (!hidden.isEmpty()) {
            return categoryId != null
                    ? postRepository.findVisibleInCategoryExcluding(blog.getId(), PostType.BLOG, categoryId, hidden,
                            request)
                    : postRepository.findVisibleInBlogExcluding(blog.getId(), List.of(PostType.BLOG), hidden, request);
        }
        if (categoryId != null) {
            return postRepository.findVisibleInCategory(blog.getId(), PostType.BLOG, categoryId, request);
        }
        return postRepository.findVisibleInBlog(blog.getId(), List.of(PostType.BLOG), request);
    }

    private List<PostListItem> toItems(List<Post> posts, Map<Long, String> names, PostListContext context) {
        return posts.stream()
                .map(p -> new PostListItem(p.getId(), p.getTitle(),
                        UserDisplayService.authorName(names, p.getUserId(), p.isAuthorDetached()), p.isNotice(),
                        context.categoryNames.get(p.getId()), context.tags.getOrDefault(p.getId(), List.of()),
                        context.thumbnails.get(p.getId()), p.getViewCount(), p.getLikeCount(), p.getCommentCount(),
                        Times.toOffset(p.getCreatedAt())))
                .toList();
    }

    private static Set<Long> authorIds(List<Post> posts) {
        Set<Long> ids = new HashSet<>();
        posts.forEach(p -> ids.add(p.getUserId()));
        return ids;
    }

    /** 블로그 글(블로그 공지 포함)만. 지워졌거나 숨긴 글, 메인 공지는 여기서 404. */
    /**
     * 보는 사람 기준으로 글을 찾는다. 관리자는 숨긴 글도 볼 수 있고 활동 기록에 남는다 (ADM-03, ADM-06, D-106).
     * 지운 글은 관리자에게도 없다.
     */
    public Post findBlogPostFor(Long postId, AuthenticatedUser principal) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post != null && post.getBlogId() != null && !post.isDeleted() && post.isHidden()
                && principal != null && principal.role() == UserRole.ADMIN) {
            adminActionLogger.logView(principal.id(), "POST", post.getId(), "숨긴 글 열람");
            return post;
        }
        return findBlogPost(postId);
    }

    public Post findBlogPost(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || !post.isVisible() || post.getBlogId() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "글을 찾을 수 없습니다.");
        }
        return post;
    }

    public Blog blogOf(Post post) {
        return blogRepository.findById(post.getBlogId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다."));
    }

    /** 작성자 본인이고, 아직 그 블로그의 멤버라 연결이 끊기지 않았는지 (D-07, D-33). */
    private boolean isActiveAuthor(Post post, Blog blog, AuthenticatedUser principal) {
        return post.getUserId().equals(principal.id()) && !post.isAuthorDetached()
                && accessService.activeMembership(blog.getId(), principal.id()) != null;
    }

    private static void rejectAdmin(AuthenticatedUser principal) {
        if (principal.role() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_NOT_ALLOWED", "관리자 계정은 블로그 글을 쓸 수 없습니다.");
        }
    }
}
