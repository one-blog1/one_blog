package com.oneblog.category;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.oneblog.blog.Blog;
import com.oneblog.common.security.AuthenticatedUser;
import com.oneblog.post.Post;
import com.oneblog.post.PostExtension;
import com.oneblog.post.PostListContext;
import com.oneblog.post.PostView;
import com.oneblog.post.dto.PostRequest;

/** 글의 카테고리: 저장 전 이 블로그 것인지 확인하고, 상세·목록에 이름을 채운다 (BRD-03). */
@Component
public class CategoryExtension implements PostExtension {

    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;

    public CategoryExtension(CategoryService categoryService, CategoryRepository categoryRepository) {
        this.categoryService = categoryService;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void validate(Blog blog, PostRequest request) {
        if (request.categoryId() != null) {
            categoryService.requireInBlog(blog.getId(), request.categoryId());
        }
    }

    @Override
    public void afterSave(Blog blog, Post post, PostRequest request, AuthenticatedUser principal) {
        post.setCategoryId(request.categoryId());
    }

    @Override
    public void describe(Blog blog, Post post, AuthenticatedUser principal, PostView view) {
        if (post.getCategoryId() != null) {
            categoryRepository.findById(post.getCategoryId()).filter(c -> !c.isDeleted())
                    .ifPresent(c -> view.categoryName = c.getName());
        }
    }

    @Override
    public void describeList(Blog blog, PostListContext context) {
        Set<Long> ids = new HashSet<>();
        context.posts.forEach(p -> {
            if (p.getCategoryId() != null) {
                ids.add(p.getCategoryId());
            }
        });
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        categoryRepository.findByIds(ids).stream().filter(c -> !c.isDeleted())
                .forEach(c -> names.put(c.getId(), c.getName()));
        context.posts.forEach(p -> {
            if (p.getCategoryId() != null && names.containsKey(p.getCategoryId())) {
                context.categoryNames.put(p.getId(), names.get(p.getCategoryId()));
            }
        });
    }
}
