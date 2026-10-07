-- 013 제재·블랙리스트·신고·차단 (BLG-11~13, SOC-05, SOC-06, ADM-02, ADM-04, ADM-07, 3.7)
-- Crowfoot ERD "One Blog 메인블로그"의 blog_blacklists, blacklist_inquiries, sanctions, reports, blocks를 그대로 가져온다.

CREATE TABLE blog_blacklists (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    name_hash CHAR(64) NOT NULL COMMENT '이름 해시',
    email_hash CHAR(64) NOT NULL COMMENT '이메일 해시',
    phone_hash CHAR(64) NOT NULL COMMENT '전화번호 해시',
    reason VARCHAR(500) COMMENT '사유',
    created_by_user_id BIGINT NOT NULL COMMENT '등록한 회원 ID-----FK 없음',
    released_at DATETIME(6) COMMENT '해제 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '등록 시각',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그 블랙리스트-----강퇴한 멤버의 이름·이메일·전화번호 해시. 고치거나 지울 수 없고 해제만 한다 (BLG-11, D-55)';

CREATE TABLE blacklist_inquiries (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    blacklist_id BIGINT COMMENT '블랙리스트 ID',
    user_id BIGINT NOT NULL COMMENT '문의한 회원 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    message VARCHAR(1000) NOT NULL COMMENT '문의 내용',
    name_matched TINYINT(1) NOT NULL DEFAULT 0 COMMENT '이름 일치 여부',
    phone_matched TINYINT(1) NOT NULL DEFAULT 0 COMMENT '전화번호 일치 여부',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, RELEASED, REJECTED',
    processed_at DATETIME(6) COMMENT '처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '문의 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_blacklist_inquiries_status CHECK (status IN ('PENDING', 'RELEASED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블랙리스트 해제 문의';

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 ID',
    blog_id BIGINT COMMENT '접수 블로그 ID-----블로그장이 처리할 신고만. 메인 관리자 접수는 NULL',
    reporter_id BIGINT NOT NULL COMMENT '신고한 회원 ID',
    target_type VARCHAR(20) NOT NULL COMMENT '대상 종류-----USER, PROFILE, BLOG, POST, COMMENT',
    target_id BIGINT NOT NULL COMMENT '대상 ID',
    target_snapshot JSON NOT NULL COMMENT '신고 당시 대상 내용',
    reason_code VARCHAR(30) NOT NULL COMMENT '사유',
    reason_detail VARCHAR(500) COMMENT '사유 상세',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '상태-----PENDING, RESOLVED',
    result VARCHAR(30) COMMENT '처리 결과-----NO_ISSUE, WARNING, SUSPENSION, KICK, CONTENT_DELETED, OWNER_WARNING, OWNER_REVOKED, BLOG_CLOSED',
    processed_by_user_id BIGINT COMMENT '처리한 회원 ID-----FK 없음. 신고 대상 본인은 처리 불가',
    processed_at DATETIME(6) COMMENT '처리 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '신고 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_reports_target_type CHECK (target_type IN ('USER', 'PROFILE', 'BLOG', 'POST', 'COMMENT')),
    CONSTRAINT ck_reports_status CHECK (status IN ('PENDING', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='신고-----대상은 target_type + target_id, 신고 당시 내용은 target_snapshot. blog_id가 있으면 그 블로그장, 없으면 메인 관리자가 처리 (D-85, D-95). 1년 보관';

CREATE TABLE sanctions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    report_id BIGINT COMMENT '근거 신고 ID',
    user_id BIGINT NOT NULL COMMENT '대상 회원 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    type VARCHAR(20) NOT NULL COMMENT '종류-----WARNING, SUSPENSION, KICK, OWNER_WARNING, OWNER_REVOKE',
    issuer_type VARCHAR(20) NOT NULL COMMENT '처분 주체-----BLOG(블로그장·부블로그장), ADMIN',
    issued_by_user_id BIGINT NOT NULL COMMENT '처분한 회원 ID-----FK 없음',
    reason VARCHAR(500) NOT NULL COMMENT '사유',
    duration_days SMALLINT COMMENT '정지 일수-----3, 14, 30. NULL이면 영구 (정지일 때만)',
    ends_at DATETIME(6) COMMENT '정지 종료 시각',
    released_at DATETIME(6) COMMENT '해제 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '처분 시각',
    PRIMARY KEY (id),
    CONSTRAINT ck_sanctions_type CHECK (type IN ('WARNING', 'SUSPENSION', 'KICK', 'OWNER_WARNING', 'OWNER_REVOKE')),
    CONSTRAINT ck_sanctions_issuer CHECK (issuer_type IN ('BLOG', 'ADMIN')),
    CONSTRAINT ck_sanctions_duration CHECK (duration_days IS NULL OR duration_days IN (3, 14, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='제재 기록-----블로그장의 멤버 경고·정지·강제 퇴장과 관리자의 블로그장 경고·권한 박탈. 횟수는 블로그 + 사람 기준, 1년 보관 (3.7)';

CREATE TABLE blocks (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '차단한 회원 ID',
    blocked_user_id BIGINT NOT NULL COMMENT '차단당한 회원 ID-----FK 없음',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '차단 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_blocks_user_id_blocked_user_id UNIQUE (user_id, blocked_user_id),
    CONSTRAINT ck_blocks_self CHECK (user_id <> blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='차단';

ALTER TABLE blog_blacklists ADD CONSTRAINT fk_blog_blacklists_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blacklist_inquiries ADD CONSTRAINT fk_blacklist_inquiries_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blacklist_inquiries ADD CONSTRAINT fk_blacklist_inquiries_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blacklist_inquiries ADD CONSTRAINT fk_blacklist_inquiries_blog_blacklists FOREIGN KEY (blacklist_id) REFERENCES blog_blacklists (id) ON DELETE SET NULL;
ALTER TABLE sanctions ADD CONSTRAINT fk_sanctions_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE sanctions ADD CONSTRAINT fk_sanctions_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE reports ADD CONSTRAINT fk_reports_users FOREIGN KEY (reporter_id) REFERENCES users (id);
ALTER TABLE reports ADD CONSTRAINT fk_reports_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE sanctions ADD CONSTRAINT fk_sanctions_reports FOREIGN KEY (report_id) REFERENCES reports (id) ON DELETE SET NULL;
ALTER TABLE blocks ADD CONSTRAINT fk_blocks_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_blog_blacklists_blog_id_email_hash ON blog_blacklists (blog_id ASC, email_hash ASC);
CREATE INDEX idx_blog_blacklists_blog_id_phone_hash ON blog_blacklists (blog_id ASC, phone_hash ASC);
CREATE INDEX idx_blacklist_inquiries_blog_id_status ON blacklist_inquiries (blog_id ASC, status ASC);
CREATE INDEX idx_sanctions_blog_id_user_id_type_created_at ON sanctions (blog_id ASC, user_id ASC, type ASC, created_at ASC);
CREATE INDEX idx_reports_reporter_id_target_type_target_id_created_at ON reports (reporter_id ASC, target_type ASC, target_id ASC, created_at DESC);
CREATE INDEX idx_reports_blog_id_status ON reports (blog_id ASC, status ASC);
CREATE INDEX idx_reports_status_created_at ON reports (status ASC, created_at ASC);
CREATE INDEX idx_blocks_blocked_user_id ON blocks (blocked_user_id ASC);
