-- 014 공유·조회수 (BRD-07, BRD-11, 6.2, 6.7, D-77)
-- Crowfoot ERD "One Blog 메인블로그"의 post_views를 그대로 가져온다.

CREATE TABLE post_views (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    post_id BIGINT NOT NULL COMMENT '글 ID',
    viewer_key CHAR(64) NOT NULL COMMENT '조회자 키-----회원은 회원 ID, 비회원은 IP+브라우저 정보 해시',
    view_date DATE NOT NULL COMMENT '조회 날짜',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '조회 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_post_views_post_id_viewer_key_view_date UNIQUE (post_id, viewer_key, view_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='글 조회 기록-----같은 사람·같은 날·같은 글은 1번만 센다 (BRD-11, D-77)';

ALTER TABLE post_views ADD CONSTRAINT fk_post_views_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
