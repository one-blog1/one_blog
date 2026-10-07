package com.oneblog.post;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    /** 블로그의 보이는 글 (지워지지 않고 숨기지 않은). 정렬은 Pageable로 (BRD-02). */
    @Query(value = """
            select p from Post p
            where p.blogId = :blogId and p.postType in :types and p.deletedAt is null and p.hidden = false
            """,
            countQuery = """
            select count(p) from Post p
            where p.blogId = :blogId and p.postType in :types and p.deletedAt is null and p.hidden = false
            """)
    Page<Post> findVisibleInBlog(@Param("blogId") Long blogId, @Param("types") Collection<PostType> types,
            Pageable pageable);

    /** 카테고리별 목록 (BRD-03). categoryId가 null이면 분류 없는 글. */
    @Query(value = """
            select p from Post p
            where p.blogId = :blogId and p.postType = :type and p.deletedAt is null and p.hidden = false
              and p.categoryId = :categoryId
            """,
            countQuery = """
            select count(p) from Post p
            where p.blogId = :blogId and p.postType = :type and p.deletedAt is null and p.hidden = false
              and p.categoryId = :categoryId
            """)
    Page<Post> findVisibleInCategory(@Param("blogId") Long blogId, @Param("type") PostType type,
            @Param("categoryId") Long categoryId, Pageable pageable);

    /** 블로그 글 수 (관리자 블로그 목록, ADM-02). */
    @Query("select count(p) from Post p where p.blogId = :blogId and p.deletedAt is null")
    long countInBlog(@Param("blogId") Long blogId);

    /** 블로그를 떠나거나 강퇴된 멤버의 글을 "탈퇴한 계정"으로 (D-33). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.authorDetached = true where p.blogId = :blogId and p.userId = :userId")
    int detachAuthor(@Param("blogId") Long blogId, @Param("userId") Long userId);

    /** 좋아요·댓글 수를 DB에서 바로 더하거나 뺀다 (동시 요청에도 수가 어긋나지 않게). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    int addLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :id")
    int addCommentCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id")
    int addViewCount(@Param("id") Long id);

    List<Post> findByIdIn(Collection<Long> ids);
}
