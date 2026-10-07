-- 004 글 작성·조회·수정·삭제, 목록 (BRD-01, BRD-02, 6.6)
-- Crowfoot ERD "One Blog 메인블로그"의 posts를 가져온다 (constitution IV).
-- posts.category_id의 외래 키(fk_posts_categories)는 categories 테이블이 생기는 008에서 추가한다 (docs/roadmap.md "004의 카테고리").

CREATE TABLE posts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '글 ID',
    category_id BIGINT COMMENT '카테고리 ID',
    user_id BIGINT NOT NULL COMMENT '작성자 ID',
    blog_id BIGINT COMMENT '블로그 ID-----메인 공지는 NULL',
    post_type VARCHAR(20) NOT NULL DEFAULT 'BLOG' COMMENT '글 종류-----BLOG, BLOG_NOTICE, MAIN_NOTICE',
    title VARCHAR(30) NOT NULL COMMENT '제목',
    content TEXT NOT NULL COMMENT '본문-----마크다운 원문. 최대 5,000자는 서버에서 검사',
    author_detached TINYINT(1) NOT NULL DEFAULT 0 COMMENT '작성자 연결 끊김-----작성자가 블로그를 떠나거나 강퇴되면 true → "탈퇴한 계정" 표시 (D-33)',
    view_count INT NOT NULL DEFAULT 0 COMMENT '조회수',
    like_count INT NOT NULL DEFAULT 0 COMMENT '좋아요 수-----인기 글 기준 (D-89)',
    comment_count INT NOT NULL DEFAULT 0 COMMENT '댓글 수',
    is_hidden TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자 숨김',
    deleted_by VARCHAR(20) COMMENT '삭제한 주체-----AUTHOR, BLOG_OWNER, ADMIN (작성자 알림용)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '작성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_posts_type CHECK (post_type IN ('BLOG', 'BLOG_NOTICE', 'MAIN_NOTICE')),
    CONSTRAINT ck_posts_main_notice CHECK ((post_type = 'MAIN_NOTICE' AND blog_id IS NULL) OR (post_type <> 'MAIN_NOTICE' AND blog_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='글-----블로그 글, 블로그 공지, 메인 공지(blog_id NULL)를 한 테이블에 둔다. 본문은 마크다운 최대 5,000자 (BRD-01, D-92)';

ALTER TABLE posts ADD CONSTRAINT fk_posts_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE posts ADD CONSTRAINT fk_posts_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE FULLTEXT INDEX idx_posts_title ON posts (title) WITH PARSER ngram;
CREATE INDEX idx_posts_blog_id_created_at ON posts (blog_id ASC, created_at DESC);
CREATE INDEX idx_posts_user_id_created_at ON posts (user_id ASC, created_at DESC);
CREATE INDEX idx_posts_like_count ON posts (like_count DESC);
CREATE INDEX idx_posts_post_type_created_at ON posts (post_type ASC, created_at DESC);
