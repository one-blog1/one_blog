-- 006 댓글·대댓글·좋아요 (BRD-06, 6.6, D-78, D-87)
-- Crowfoot ERD "One Blog 메인블로그"의 comments, post_likes를 가져온다 (constitution IV).

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글 ID',
    parent_id BIGINT COMMENT '부모 댓글 ID-----대댓글이면 첫 댓글 ID',
    user_id BIGINT NOT NULL COMMENT '작성자 ID',
    post_id BIGINT NOT NULL COMMENT '글 ID',
    reply_to_user_id BIGINT COMMENT '답글 대상 회원 ID-----@닉네임 표시용 (D-87, FK 없음)',
    content VARCHAR(500) NOT NULL COMMENT '내용',
    is_edited TINYINT(1) NOT NULL DEFAULT 0 COMMENT '수정 여부',
    is_hidden TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자 숨김',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='댓글-----댓글과 1단계 대댓글. 답글이 있는 댓글은 삭제해도 "삭제된 댓글입니다"로 남긴다 (6.6, D-78, D-87)';

CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    post_id BIGINT NOT NULL COMMENT '글 ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '누른 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_likes_post_id_user_id UNIQUE (post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='글 좋아요';

ALTER TABLE comments ADD CONSTRAINT fk_comments_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE comments ADD CONSTRAINT fk_comments_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE comments ADD CONSTRAINT fk_comments_comments FOREIGN KEY (parent_id) REFERENCES comments (id);
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_comments_post_id_parent_id_created_at ON comments (post_id ASC, parent_id ASC, created_at ASC);
CREATE INDEX idx_post_likes_user_id ON post_likes (user_id ASC);
