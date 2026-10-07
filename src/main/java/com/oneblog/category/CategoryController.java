package com.oneblog.category;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oneblog.common.security.AuthenticatedUser;

/** 카테고리 API (BRD-03). */
@RestController
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    public record CategoryRequest(String name, Integer sortOrder) {
    }

    @GetMapping("/api/blogs/{slug}/categories")
    public List<CategoryService.CategoryItem> list(@PathVariable("slug") String slug,
            @RequestParam(name = "key", required = false) String key,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return categoryService.list(slug, key, principal == null ? null : principal.id());
    }

    @PostMapping("/api/blogs/{slug}/categories")
    public ResponseEntity<CategoryService.CategoryItem> create(@PathVariable("slug") String slug,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(slug, principal, request.name()));
    }

    @PutMapping("/api/categories/{id}")
    public ResponseEntity<Void> update(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal, @RequestBody CategoryRequest request) {
        categoryService.update(id, principal, request.name(), request.sortOrder());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        categoryService.delete(id, principal);
        return ResponseEntity.noContent().build();
    }
}
