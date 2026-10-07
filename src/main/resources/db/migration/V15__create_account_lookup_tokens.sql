-- 015 계정 보안 세부: 이메일 찾기 → 비밀번호 재설정 임시 토큰 (USR-08, D-26)
-- Crowfoot ERD "One Blog 메인블로그"의 account_lookup_tokens를 그대로 가져온다.

CREATE TABLE account_lookup_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    token_hash CHAR(64) NOT NULL COMMENT '토큰 해시',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 시각',
    used_at DATETIME(6) COMMENT '사용 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_account_lookup_tokens_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='이메일 찾기 임시 토큰-----이메일 찾기 결과에서 비밀번호 재설정을 요청할 때 회원 번호 대신 쓰는 10분짜리 토큰 (USR-08, D-26)';

ALTER TABLE account_lookup_tokens ADD CONSTRAINT fk_account_lookup_tokens_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
