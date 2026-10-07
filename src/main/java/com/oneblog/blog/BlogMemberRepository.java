package com.oneblog.blog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogMemberRepository extends JpaRepository<BlogMember, Long> {

    Optional<BlogMember> findByBlogIdAndUserIdAndStatus(Long blogId, Long userId, BlogMemberStatus status);

    /** 상태와 관계없이 이 블로그·회원의 멤버십 행 (블로그를 떠났다가 다시 참여할 때 되살린다). */
    Optional<BlogMember> findByBlogIdAndUserId(Long blogId, Long userId);

    /** 지금 멤버인지 (요청마다 DB로 확인, SEC-07). */
    default Optional<BlogMember> findActive(Long blogId, Long userId) {
        return findByBlogIdAndUserIdAndStatus(blogId, userId, BlogMemberStatus.ACTIVE);
    }

    /** 블로그별 블로그장 닉네임. 결과 행은 [blogId, nickname]. */
    @Query("""
            select m.blogId, u.nickname from BlogMember m, User u
            where u.id = m.userId and m.role = :owner and m.status = :active and m.blogId in :blogIds
            """)
    List<Object[]> findOwnerNicknameRows(@Param("blogIds") Collection<Long> blogIds,
            @Param("owner") BlogRole owner, @Param("active") BlogMemberStatus active);

    default List<Object[]> findOwnerNicknames(Collection<Long> blogIds) {
        return findOwnerNicknameRows(blogIds, BlogRole.OWNER, BlogMemberStatus.ACTIVE);
    }

    /** 내 블로그: 내가 지금 멤버인 폐쇄·삭제되지 않은 블로그 (BLG-06). 결과 행은 [Blog, BlogRole]. */
    @Query("""
            select b, m.role from Blog b, BlogMember m
            where m.blogId = b.id and m.userId = :userId and m.status = :active
              and b.status <> :closed and b.deletedAt is null
            order by m.joinedAt desc, b.id desc
            """)
    List<Object[]> findMyBlogRows(@Param("userId") Long userId, @Param("active") BlogMemberStatus active,
            @Param("closed") BlogStatus closed);

    default List<Object[]> findMyBlogs(Long userId) {
        return findMyBlogRows(userId, BlogMemberStatus.ACTIVE, BlogStatus.CLOSED);
    }
}
