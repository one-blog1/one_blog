-- 007 메인 관리자 1차 (ADM-01~03, 05, 06, SEC-09, BRD-10, D-98)
-- Crowfoot ERD "One Blog 메인블로그"의 admin_actions를 가져온다 (constitution IV).
-- 메인 공지(BRD-10)는 posts(post_type = MAIN_NOTICE, blog_id NULL)를 쓴다.

CREATE TABLE admin_actions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    admin_id BIGINT NOT NULL COMMENT '관리자 ID',
    action_type VARCHAR(40) NOT NULL COMMENT '조치 종류',
    target_type VARCHAR(20) NOT NULL COMMENT '대상 종류',
    target_id BIGINT NOT NULL COMMENT '대상 ID',
    detail VARCHAR(500) COMMENT '내용',
    ip_address VARCHAR(45) COMMENT '접속 IP',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '조치 시각',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='관리자 활동 기록-----경고·강퇴·삭제·폐쇄·숨김·개인정보 전체 보기. 1년 보관 (ADM-06, 4.4)';

ALTER TABLE admin_actions ADD CONSTRAINT fk_admin_actions_users FOREIGN KEY (admin_id) REFERENCES users (id);

CREATE INDEX idx_admin_actions_created_at ON admin_actions (created_at ASC);
CREATE INDEX idx_admin_actions_target_type_target_id ON admin_actions (target_type ASC, target_id ASC);
