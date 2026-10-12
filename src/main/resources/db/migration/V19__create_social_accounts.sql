-- 022 소셜 로그인 (USR-09, D-126)
-- Crowfoot ERD "One Blog 메인블로그"의 social_accounts를 그대로 가져온다.
-- 사이트(카카오·네이버·구글)의 액세스 토큰은 저장하지 않는다. 회원 번호만 둔다.

CREATE TABLE social_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '연결 ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    provider VARCHAR(20) NOT NULL COMMENT '사이트-----KAKAO, NAVER, GOOGLE',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '사이트 회원 번호-----사이트가 주는 고유 번호(카카오 id, 네이버 response.id, 구글 sub)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '연결 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_social_accounts_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_accounts_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_social_accounts_provider CHECK (provider IN ('KAKAO', 'NAVER', 'GOOGLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='소셜 계정 연결-----회원과 카카오·네이버·구글 계정의 연결 (USR-09, D-126). 사이트의 액세스 토큰은 저장하지 않는다';

ALTER TABLE social_accounts ADD CONSTRAINT fk_social_accounts_users
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
