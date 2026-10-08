package com.oneblog.category;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 블로그 카테고리(categories) 저장소. 블로그별 목록과 순서에 쓴다 (BRD-03). */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    @Query("select c from Category c where c.blogId = :blogId and c.deletedAt is null order by c.sortOrder asc, c.id asc")
    List<Category> findLive(@Param("blogId") Long blogId);

    @Query("select c from Category c where c.id in :ids")
    List<Category> findByIds(@Param("ids") Collection<Long> ids);

    /** 카테고리별 보이는 글 수. 결과 행은 [categoryId, count]. */
    @Query("""
            select p.categoryId, count(p) from Post p
            where p.blogId = :blogId and p.categoryId is not null and p.deletedAt is null and p.hidden = false
            group by p.categoryId
            """)
    List<Object[]> countPosts(@Param("blogId") Long blogId);

    /** 카테고리를 지우면 그 글은 "분류 없음"이 된다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.categoryId = null where p.categoryId = :categoryId")
    int clearFromPosts(@Param("categoryId") Long categoryId);
}
