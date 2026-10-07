-- 008 카테고리·태그 (BRD-03, BRD-04, 6.4)
-- Crowfoot ERD "One Blog 메인블로그"의 categories, post_tags를 가져오고, 004에서 미룬 fk_posts_categories를 추가한다.

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '카테고리 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    name VARCHAR(30) NOT NULL COMMENT '이름',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '순서',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='카테고리-----블로그별 글 분류. 블로그장이 관리 (BRD-03)';

CREATE TABLE post_tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    tag_id BIGINT NOT NULL COMMENT '태그 ID',
    post_id BIGINT NOT NULL COMMENT '글 ID',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_tags UNIQUE (post_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='글 태그';

ALTER TABLE categories ADD CONSTRAINT fk_categories_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_categories FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL;
ALTER TABLE post_tags ADD CONSTRAINT fk_post_tags_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE post_tags ADD CONSTRAINT fk_post_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id);

CREATE INDEX idx_post_tags_tag_id ON post_tags (tag_id ASC);
