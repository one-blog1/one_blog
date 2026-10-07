-- 012 블로그 운영: 위임·폐쇄·블로그 탈퇴, 회원탈퇴, 04:00 배치 (BLG-07~09, USR-05, 4.5, D-83)
-- Crowfoot ERD "One Blog 메인블로그"의 blog_transfer_requests, shedlock을 그대로 가져온다.

CREATE TABLE blog_transfer_requests (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    to_user_id BIGINT NOT NULL COMMENT '받는 멤버 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    from_user_id BIGINT NOT NULL COMMENT '요청한 블로그장 ID-----FK 없음',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, ACCEPTED, REJECTED, CANCELED, EXPIRED',
    expires_at DATETIME(6) NOT NULL COMMENT '자동 취소 시각',
    responded_at DATETIME(6) COMMENT '응답 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '요청 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_blog_transfer_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그장 위임 요청-----받는 멤버가 수락해야 넘어가고 7일 안에 응답이 없으면 자동 취소 (BLG-08)';

CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL COMMENT '작업 이름',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '잠금 끝',
    locked_at TIMESTAMP(3) NOT NULL COMMENT '잠근 시각',
    locked_by VARCHAR(255) NOT NULL COMMENT '잠근 서버',
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='스케줄러 잠금-----ShedLock 기본 테이블. @Scheduled 작업이 한 서버에서만 돌게 한다 (SCL-03)';

ALTER TABLE blog_transfer_requests ADD CONSTRAINT fk_blog_transfer_requests_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_transfer_requests ADD CONSTRAINT fk_blog_transfer_requests_users FOREIGN KEY (to_user_id) REFERENCES users (id);

CREATE INDEX idx_blog_transfer_requests_blog_id_status ON blog_transfer_requests (blog_id ASC, status ASC);
CREATE INDEX idx_blog_transfer_requests_status_expires_at ON blog_transfer_requests (status ASC, expires_at ASC);
