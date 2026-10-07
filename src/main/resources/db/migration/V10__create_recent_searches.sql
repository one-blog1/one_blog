-- 010 통합 검색·메인 피드 (BRD-08, BRD-09, BLG-03, 6.1)
-- Crowfoot ERD "One Blog 메인블로그"의 recent_searches를 그대로 가져온다.

CREATE TABLE recent_searches (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    keyword VARCHAR(20) NOT NULL COMMENT '검색어',
    searched_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '검색 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_recent_searches_user_id_keyword UNIQUE (user_id, keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='최근 검색어-----본인만, 최대 10개 (6.1)';

ALTER TABLE recent_searches ADD CONSTRAINT fk_recent_searches_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_recent_searches_user_id_searched_at ON recent_searches (user_id ASC, searched_at DESC);
