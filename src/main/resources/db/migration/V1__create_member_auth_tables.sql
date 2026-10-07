-- 001 회원가입·로그인·로그아웃 (USR-01~04)
-- Crowfoot ERD "One Blog 메인블로그"에서 users, verification_codes, refresh_tokens만 가져온다 (constitution IV).
-- 이 파일은 적용한 뒤에는 고치지 않는다. 바꿀 일이 있으면 ERD를 먼저 고치고 새 버전 파일을 만든다.

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email VARCHAR(255) COMMENT '이메일-----일반 회원의 로그인 아이디. 소문자로 저장. 탈퇴 즉시 NULL. 관리자는 NULL 가능',
    login_id VARCHAR(30) COMMENT '관리자 아이디-----관리자 전용 로그인 아이디(예: admin01). 일반 회원은 NULL (D-98)',
    password_hash VARCHAR(100) NOT NULL COMMENT '비밀번호 해시-----bcrypt (SEC-01)',
    name VARCHAR(50) COMMENT '이름-----변경 불가. 탈퇴 30일 뒤 NULL',
    nickname VARCHAR(12) COMMENT '닉네임-----2~12자, 한글·영문·숫자, 중복 불가',
    phone VARCHAR(20) COMMENT '전화번호-----숫자만. 1차는 중복 허용',
    profile_image_url VARCHAR(500) COMMENT '프로필 사진 경로',
    bio VARCHAR(300) COMMENT '소개',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '권한-----USER, ADMIN',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태-----ACTIVE, WITHDRAWN',
    failed_login_count INT NOT NULL DEFAULT 0 COMMENT '연속 로그인 실패 횟수',
    locked_until DATETIME(6) COMMENT '로그인 잠금 해제 시각',
    terms_agreed_at DATETIME(6) COMMENT '이용약관 동의 시각',
    privacy_agreed_at DATETIME(6) COMMENT '개인정보 동의 시각',
    notification_retention_days TINYINT NOT NULL DEFAULT 30 COMMENT '알림 보관 일수-----30 또는 7',
    withdrawn_at DATETIME(6) COMMENT '탈퇴 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '가입 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    CONSTRAINT uk_users_nickname UNIQUE (nickname),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'WITHDRAWN')),
    CONSTRAINT ck_users_login_id CHECK ((role = 'ADMIN' AND login_id IS NOT NULL) OR (role = 'USER' AND login_id IS NULL)),
    CONSTRAINT ck_users_retention CHECK (notification_retention_days IN (7, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='회원-----일반 회원과 관리자 계정(role로 구분). 탈퇴하면 email을 비우고 30일 뒤 개인정보를 지운다.';

CREATE TABLE verification_codes (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    email VARCHAR(255) NOT NULL COMMENT '이메일',
    purpose VARCHAR(20) NOT NULL COMMENT '용도-----SIGNUP, PASSWORD_RESET',
    code_hash VARCHAR(100) NOT NULL COMMENT '인증번호 해시',
    fail_count TINYINT NOT NULL DEFAULT 0 COMMENT '틀린 횟수-----5번이면 무효',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 시각',
    verified_at DATETIME(6) COMMENT '인증 완료 시각',
    used_at DATETIME(6) COMMENT '사용 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '발송 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_verification_codes_purpose CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='이메일 인증번호-----가입 인증(10분)과 비밀번호 재설정(30분, 1회용) 6자리 코드. 가입 전이라 회원 대신 이메일로 묶는다. 만료·사용 즉시 삭제.';

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    token_hash CHAR(64) NOT NULL COMMENT '토큰 해시',
    remember_me TINYINT(1) NOT NULL DEFAULT 0 COMMENT '로그인 유지 여부-----false면 30분 무활동 시 만료, true면 14일',
    last_activity_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '마지막 활동 시각-----사용자가 직접 한 요청만 갱신 (D-62)',
    expires_at DATETIME(6) NOT NULL COMMENT '만료 시각',
    revoked_at DATETIME(6) COMMENT '폐기 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '발급 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Refresh Token-----로그인 유지용 토큰. 로그아웃·비밀번호 변경 때 폐기하고, 만료분은 04:00 배치가 지운다 (D-82, D-83)';

ALTER TABLE refresh_tokens ADD CONSTRAINT fk_refresh_tokens_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_users_name_phone ON users (name ASC, phone ASC);
CREATE INDEX idx_verification_codes_email_purpose_created_at ON verification_codes (email ASC, purpose ASC, created_at DESC);
CREATE INDEX idx_verification_codes_expires_at ON verification_codes (expires_at ASC);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at ASC);
