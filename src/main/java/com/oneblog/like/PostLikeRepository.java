package com.oneblog.like;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 글 좋아요(post_likes) 저장소. 좋아요 여부와 취소에 쓴다 (BRD-06). */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    boolean existsByPostIdAndUserId(Long postId, Long userId);
}
