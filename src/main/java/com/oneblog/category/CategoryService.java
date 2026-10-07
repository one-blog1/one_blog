package com.oneblog.category;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneblog.blog.Blog;
import com.oneblog.blog.BlogAccessService;
import com.oneblog.blog.BlogMember;
import com.oneblog.blog.BlogRepository;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.common.web.ApiException;
import com.oneblog.common.web.ErrorResponse;
import com.oneblog.member.ValidationFailedException;

/**
 * 카테고리 관리 (BRD-03). 블로그장과 글 관리 권한을 받은 부블로그장이 만들고·이름을 바꾸고·순서를 정하고·지운다 (2장).
 * 이름은 1~30자이고 한 블로그 안에서 겹칠 수 없다.
 */
@Service
public class CategoryService {

    public static final int NAME_MAX = 30;
    public static final int MAX_PER_BLOG = 50;

    private final CategoryRepository categoryRepository;
    private final BlogAccessService accessService;
    private final BlogRepository blogRepository;

    public CategoryService(CategoryRepository categoryRepository, BlogAccessService accessService,
            BlogRepository blogRepository) {
        this.categoryRepository = categoryRepository;
        this.accessService = accessService;
        this.blogRepository = blogRepository;
    }

    public record CategoryItem(Long id, String name, int sortOrder, long postCount) {
    }

    @Transactional(readOnly = true)
    public List<CategoryItem> list(String slug, String key, Long viewerId) {
        Blog blog = accessService.check(slug, key, viewerId).blog();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : categoryRepository.countPosts(blog.getId())) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findLive(blog.getId()).stream()
                .map(c -> new CategoryItem(c.getId(), c.getName(), c.getSortOrder(), counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryItem create(String slug, AuthenticatedUser principal, String rawName) {
        Blog blog = requireManager(accessService.check(slug, null, principal.id()).blog(), principal);
        String name = validateName(rawName);
        List<Category> live = categoryRepository.findLive(blog.getId());
        if (live.size() >= MAX_PER_BLOG) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_LIMIT", "카테고리는 50개까지 만들 수 있습니다.");
        }
        ensureUnique(live, name, null);
        int order = live.stream().mapToInt(Category::getSortOrder).max().orElse(-1) + 1;
        Category saved = categoryRepository.save(Category.create(blog.getId(), name, order));
        return new CategoryItem(saved.getId(), saved.getName(), saved.getSortOrder(), 0);
    }

    @Transactional
    public void update(Long categoryId, AuthenticatedUser principal, String rawName, Integer sortOrder) {
        Category category = findLive(categoryId);
        Blog blog = requireManager(blogOf(category), principal);
        if (rawName != null) {
            String name = validateName(rawName);
            ensureUnique(categoryRepository.findLive(blog.getId()), name, categoryId);
            category.rename(name);
        }
        if (sortOrder != null) {
            category.moveTo(Math.max(0, sortOrder));
        }
    }

    @Transactional
    public void delete(Long categoryId, AuthenticatedUser principal) {
        Category category = findLive(categoryId);
        requireManager(blogOf(category), principal);
        category.delete();
        categoryRepository.clearFromPosts(categoryId);
    }

    /** 글에 붙일 카테고리가 이 블로그의 살아 있는 카테고리인지 (CategoryExtension). */
    @Transactional(readOnly = true)
    public Category requireInBlog(Long blogId, Long categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(c -> !c.isDeleted() && c.getBlogId().equals(blogId))
                .orElseThrow(() -> new ValidationFailedException(List.of(
                        new ErrorResponse.FieldError("categoryId", "이 블로그의 카테고리를 골라 주세요."))));
    }

    private Category findLive(Long categoryId) {
        return categoryRepository.findById(categoryId).filter(c -> !c.isDeleted())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
    }

    private Blog blogOf(Category category) {
        Blog blog = blogRepository.findById(category.getBlogId()).filter(Blog::isOpen)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BLOG_NOT_FOUND", "블로그를 찾을 수 없습니다."));
        return blog;
    }

    private Blog requireManager(Blog blog, AuthenticatedUser principal) {
        BlogMember member = accessService.activeMembership(blog.getId(), principal.id());
        if (member == null || !member.canManagePosts()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "카테고리는 블로그장(글 관리 권한)만 관리할 수 있습니다.");
        }
        return blog;
    }

    private static String validateName(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > NAME_MAX) {
            throw new ValidationFailedException(List.of(
                    new ErrorResponse.FieldError("name", "카테고리 이름은 1~30자로 입력해 주세요.")));
        }
        return name;
    }

    private static void ensureUnique(List<Category> live, String name, Long exceptId) {
        boolean taken = live.stream().anyMatch(c -> !c.getId().equals(exceptId) && c.getName().equalsIgnoreCase(name));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_TAKEN", "같은 이름의 카테고리가 있습니다.");
        }
    }
}
