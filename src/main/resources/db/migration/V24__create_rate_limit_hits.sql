-- 032 요청 제한 기록 공유 (SEC-03, D-80, SCL-01, D-143)
-- Crowfoot ERD "One Blog 메인블로그"의 rate_limit_hits를 그대로 가져온다 (REQ-036).
-- 운영 앱 2개(D-135)가 같은 기록으로 센다. 대상(IP·회원 ID)은 원문 대신 HMAC 해시 (REQ-002).

CREATE TABLE rate_limit_hits (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    action VARCHAR(30) NOT NULL COMMENT '제한 이름-----login, signup-code, password-reset, find-email, reauth, report, toggle',
    key_hash CHAR(64) NOT NULL COMMENT '대상 해시-----IP 또는 회원 ID의 HMAC-SHA256 (16진수)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '요청 시각-----DB 시각',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='요청 제한 기록-----IP·회원별 요청 제한의 최근 요청 시각. 앱 여러 개가 같이 센다 (SEC-03, D-80, SCL-01). 1시간 지나면 지운다.';

CREATE INDEX idx_rate_limit_hits_action_key_hash_created_at ON rate_limit_hits (action ASC, key_hash ASC, created_at ASC);
CREATE INDEX idx_rate_limit_hits_created_at ON rate_limit_hits (created_at ASC);
