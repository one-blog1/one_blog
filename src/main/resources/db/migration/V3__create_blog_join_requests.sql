-- 003 블로그 참여 신청·승인 (BLG-04, BLG-05, 6.5)
-- Crowfoot ERD "One Blog 메인블로그"의 blog_join_requests를 그대로 가져온다 (constitution IV).

CREATE TABLE blog_join_requests (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '신청한 회원 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, APPROVED, REJECTED, CANCELED',
    processed_by_user_id BIGINT COMMENT '처리한 회원 ID-----블로그장 또는 부블로그장 (FK 없음)',
    processed_at DATETIME(6) COMMENT '처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '신청 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_blog_join_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='참여 신청-----승인제 블로그의 참여 신청. 거절되면 7일 뒤 재신청 (6.5)';

ALTER TABLE blog_join_requests ADD CONSTRAINT fk_blog_join_requests_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_join_requests ADD CONSTRAINT fk_blog_join_requests_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_blog_join_requests_blog_id_status ON blog_join_requests (blog_id ASC, status ASC);
CREATE INDEX idx_blog_join_requests_user_id_blog_id_created_at ON blog_join_requests (user_id ASC, blog_id ASC, created_at DESC);
