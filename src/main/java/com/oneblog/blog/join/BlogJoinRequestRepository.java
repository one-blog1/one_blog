package com.oneblog.blog.join;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BlogJoinRequestRepository extends JpaRepository<BlogJoinRequest, Long> {

    /** 이 회원의 이 블로그 신청 중 가장 최근 것. */
    Optional<BlogJoinRequest> findFirstByBlogIdAndUserIdOrderByCreatedAtDescIdDesc(Long blogId, Long userId);

    /** 처리할 때 같은 신청을 두 사람이 동시에 처리하지 못하게 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BlogJoinRequest r where r.id = :id")
    Optional<BlogJoinRequest> findForUpdateById(@Param("id") Long id);

    /** 블로그의 대기 중인 신청과 신청자 닉네임 (오래된 순). 결과 행은 [BlogJoinRequest, nickname]. */
    @Query("""
            select r, u.nickname from BlogJoinRequest r, User u
            where u.id = r.userId and r.blogId = :blogId and r.status = :status
            order by r.createdAt asc, r.id asc
            """)
    List<Object[]> findWithNickname(@Param("blogId") Long blogId, @Param("status") JoinRequestStatus status);

    default List<Object[]> findPendingWithNickname(Long blogId) {
        return findWithNickname(blogId, JoinRequestStatus.PENDING);
    }
}
