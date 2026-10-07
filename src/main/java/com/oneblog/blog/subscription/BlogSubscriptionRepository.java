package com.oneblog.blog.subscription;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlogSubscriptionRepository extends JpaRepository<BlogSubscription, Long> {

    Optional<BlogSubscription> findByBlogIdAndUserId(Long blogId, Long userId);

    boolean existsByBlogIdAndUserId(Long blogId, Long userId);

    long countByBlogId(Long blogId);

    /** 블로그 구독자 ID (폐쇄·비공개 전환 알림, 011·012). */
    @Query("select s.userId from BlogSubscription s where s.blogId = :blogId")
    List<Long> findUserIds(@Param("blogId") Long blogId);
}
