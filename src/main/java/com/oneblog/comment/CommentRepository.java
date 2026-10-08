package com.oneblog.comment;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 댓글(comments) 저장소. 글별 댓글 목록과 대댓글에 쓴다 (BRD-06). */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** 글의 댓글 전부(지운 것 포함, 화면이 "삭제된 댓글입니다"를 판단). 첫 댓글 → 대댓글 순으로 오래된 것부터. */
    @Query("select c from Comment c where c.postId = :postId order by c.createdAt asc, c.id asc")
    List<Comment> findByPost(@Param("postId") Long postId);

    /** 지워지지 않은 대댓글이 있는지. */
    boolean existsByParentIdAndDeletedAtIsNull(Long parentId);
}
