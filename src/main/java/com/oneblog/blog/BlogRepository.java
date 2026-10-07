package com.oneblog.blog;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** 메인 목록: 공개이고 폐쇄·숨김·삭제되지 않은 블로그 (BLG-02). 정렬은 Pageable로 받는다 (research R8). */
    @Query(value = """
            select b from Blog b
            where b.visibility = :visibility and b.status <> :closed and b.hidden = false and b.deletedAt is null
            """,
            countQuery = """
            select count(b) from Blog b
            where b.visibility = :visibility and b.status <> :closed and b.hidden = false and b.deletedAt is null
            """)
    Page<Blog> findListed(@Param("visibility") BlogVisibility visibility, @Param("closed") BlogStatus closed,
            Pageable pageable);

    default Page<Blog> findPublicList(Pageable pageable) {
        return findListed(BlogVisibility.PUBLIC, BlogStatus.CLOSED, pageable);
    }

    /** 회원이 블로그장인, 폐쇄·삭제되지 않은 블로그 수 (BLG-10, research R3). */
    @Query("""
            select count(b) from Blog b, BlogMember m
            where m.blogId = b.id and m.userId = :userId and m.role = :owner and m.status = :active
              and b.status <> :closed and b.deletedAt is null and b.visibility in :visibilities
            """)
    long countOwnedBlogs(@Param("userId") Long userId, @Param("visibilities") Collection<BlogVisibility> visibilities,
            @Param("owner") BlogRole owner, @Param("active") BlogMemberStatus active,
            @Param("closed") BlogStatus closed);

    default long countOwned(Long userId, Collection<BlogVisibility> visibilities) {
        return countOwnedBlogs(userId, visibilities, BlogRole.OWNER, BlogMemberStatus.ACTIVE, BlogStatus.CLOSED);
    }
}
