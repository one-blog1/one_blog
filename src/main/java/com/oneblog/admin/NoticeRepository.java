package com.oneblog.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.oneblog.post.Post;

/** 메인 공지(posts.post_type = MAIN_NOTICE) 조회. */
public interface NoticeRepository extends JpaRepository<Post, Long> {

    @Query(value = """
            select p from Post p
            where p.postType = PostType.MAIN_NOTICE and p.deletedAt is null and p.hidden = false
            """,
            countQuery = """
            select count(p) from Post p
            where p.postType = PostType.MAIN_NOTICE and p.deletedAt is null and p.hidden = false
            """)
    Page<Post> findMainNotices(Pageable pageable);
}
