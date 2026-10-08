package com.oneblog.blog.ops;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** 블로그장 위임 요청(blog_transfer_requests) 저장소 (BLG-08). */
public interface BlogTransferRequestRepository extends JpaRepository<BlogTransferRequest, Long> {

    List<BlogTransferRequest> findByBlogIdAndStatus(Long blogId, TransferStatus status);

    List<BlogTransferRequest> findByToUserIdAndStatusOrderByCreatedAtDesc(Long toUserId, TransferStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BlogTransferRequest r where r.id = :id")
    Optional<BlogTransferRequest> findForUpdateById(@Param("id") Long id);

    @Query("select r from BlogTransferRequest r where r.status = :status and r.expiresAt <= :now")
    List<BlogTransferRequest> findExpiredRows(@Param("status") TransferStatus status, @Param("now") LocalDateTime now);

    default List<BlogTransferRequest> findExpired(LocalDateTime now) {
        return findExpiredRows(TransferStatus.PENDING, now);
    }

    default Optional<BlogTransferRequest> findPending(Long blogId) {
        return findByBlogIdAndStatus(blogId, TransferStatus.PENDING).stream().findFirst();
    }
}
