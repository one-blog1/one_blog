package com.oneblog.blog;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogRepository extends JpaRepository<Blog, Long> {

    Optional<Blog> findBySlug(String slug);

    /** 멤버 수를 DB에서 바로 더하거나 뺀다 (동시에 참여해도 수가 어긋나지 않게, D-89 인기순 기준). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Blog b set b.memberCount = b.memberCount + :delta where b.id = :id")
    int addMemberCount(@Param("id") Long id, @Param("delta") int delta);

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

    /** 내가 구독한 폐쇄·삭제되지 않은 블로그. 결과 행은 [Blog, 구독 시각]. */
    @Query("""
            select b, s.createdAt from Blog b, BlogSubscription s
            where s.blogId = b.id and s.userId = :userId and b.status <> :closed and b.deletedAt is null
            order by s.createdAt desc, s.id desc
            """)
    java.util.List<Object[]> findSubscribedRows(@Param("userId") Long userId, @Param("closed") BlogStatus closed);

    default java.util.List<Object[]> findSubscribedRows(Long userId) {
        return findSubscribedRows(userId, BlogStatus.CLOSED);
    }
}
