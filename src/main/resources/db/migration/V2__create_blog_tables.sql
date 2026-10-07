-- 002 블로그 생성·목록·내 블로그 (BLG-01, BLG-02, BLG-06, BLG-10)
-- Crowfoot ERD "One Blog 메인블로그"(버전 6)에서 blogs, blog_members, tags, blog_tags, files만 가져온다 (constitution IV).
-- files.post_id의 외래 키(fk_files_posts)는 posts 테이블이 생기는 005에서 추가한다 (docs/roadmap.md "002의 대표 이미지").
-- 이 파일은 적용한 뒤에는 고치지 않는다. 바꿀 일이 있으면 ERD를 먼저 고치고 새 버전 파일을 만든다.

CREATE TABLE blogs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '블로그 ID',
    slug VARCHAR(30) COMMENT '블로그 주소-----/blog/{slug}. 영문 소문자·숫자·- 3~30자. 폐쇄 30일 뒤 NULL',
    name VARCHAR(50) NOT NULL COMMENT '블로그 이름',
    description VARCHAR(500) COMMENT '소개',
    cover_image_url VARCHAR(500) COMMENT '대표 이미지 경로',
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' COMMENT '공개 범위-----PUBLIC, UNLISTED(일부 공개), PRIVATE',
    join_policy VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '참여 방식-----OPEN(자유), APPROVAL(승인제)',
    share_token CHAR(32) COMMENT '공유 링크 토큰-----일부 공개 블로그의 무작위 값. 새로 만들면 이전 값은 무효',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태-----ACTIVE, CLOSING(폐쇄 예정), CLOSED',
    is_hidden TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자 숨김',
    close_scheduled_at DATETIME(6) COMMENT '폐쇄 예정 시각',
    close_reason VARCHAR(20) COMMENT '폐쇄 사유-----OWNER, ADMIN, OWNER_REVOKED',
    closed_at DATETIME(6) COMMENT '폐쇄 시각',
    member_count INT NOT NULL DEFAULT 1 COMMENT '멤버 수-----인기순 기준 (D-89)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_blogs_slug UNIQUE (slug),
    CONSTRAINT uk_blogs_share_token UNIQUE (share_token),
    CONSTRAINT ck_blogs_visibility CHECK (visibility IN ('PUBLIC', 'UNLISTED', 'PRIVATE')),
    CONSTRAINT ck_blogs_join_policy CHECK (join_policy IN ('OPEN', 'APPROVAL')),
    CONSTRAINT ck_blogs_status CHECK (status IN ('ACTIVE', 'CLOSING', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그-----회원이 만든 개별 블로그. 폐쇄 30일 뒤 내용만 지우고 행은 남긴다(D-86).';

CREATE TABLE blog_members (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '역할-----OWNER, SUB_OWNER, MEMBER',
    can_edit_info TINYINT(1) NOT NULL DEFAULT 0 COMMENT '블로그 정보 수정 권한-----부블로그장 체크박스 (D-71)',
    can_manage_members TINYINT(1) NOT NULL DEFAULT 0 COMMENT '멤버 관리 권한',
    can_manage_posts TINYINT(1) NOT NULL DEFAULT 0 COMMENT '글 관리 권한',
    sub_owner_since DATETIME(6) COMMENT '부블로그장 지정 시각-----블로그장 강퇴 시 가장 먼저 지정된 사람에게 넘김 (ADM-01)',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태-----ACTIVE, LEFT(탈퇴), KICKED(강제 퇴장)',
    suspended_until DATETIME(6) COMMENT '정지 종료 시각-----영구 정지는 9999-12-31',
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '참여 시각',
    left_at DATETIME(6) COMMENT '떠난 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_members_blog_id_user_id UNIQUE (blog_id, user_id),
    CONSTRAINT ck_blog_members_role CHECK (role IN ('OWNER', 'SUB_OWNER', 'MEMBER')),
    CONSTRAINT ck_blog_members_status CHECK (status IN ('ACTIVE', 'LEFT', 'KICKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그 멤버십-----회원과 블로그를 잇고 블로그별 역할을 둔다. 역할은 계정이 아니라 블로그별 속성 (2장, SEC-07)';

CREATE TABLE tags (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '태그 ID',
    name VARCHAR(30) NOT NULL COMMENT '태그 이름-----1~20자, 한글·영문·숫자·_, 영문 소문자',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_tags_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='태그-----글 태그와 블로그 태그가 함께 쓴다 (6.4, D-88)';

CREATE TABLE blog_tags (
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    tag_id BIGINT NOT NULL COMMENT '태그 ID',
    PRIMARY KEY (blog_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그 태그';

CREATE TABLE files (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '파일 ID',
    post_id BIGINT COMMENT '글 ID-----글 저장 전에 올린 이미지는 NULL',
    user_id BIGINT NOT NULL COMMENT '올린 회원 ID',
    purpose VARCHAR(20) NOT NULL COMMENT '용도-----POST, PROFILE, BLOG_COVER',
    stored_name VARCHAR(64) NOT NULL COMMENT '저장 이름-----UUID + 확장자',
    original_name VARCHAR(255) NOT NULL COMMENT '원래 이름',
    content_type VARCHAR(50) NOT NULL COMMENT 'MIME 타입',
    size_bytes INT NOT NULL COMMENT '크기(바이트)',
    sort_order SMALLINT NOT NULL DEFAULT 0 COMMENT '순서',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '업로드 시각',
    deleted_at DATETIME(6) COMMENT '삭제 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_files_stored_name UNIQUE (stored_name),
    CONSTRAINT ck_files_purpose CHECK (purpose IN ('POST', 'PROFILE', 'BLOG_COVER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='업로드 파일-----글 이미지·프로필 사진·블로그 대표 이미지. 저장은 FileStorage 뒤에 두고 UUID 이름으로 저장 (6.3, SCL-02)';

ALTER TABLE blog_members ADD CONSTRAINT fk_blog_members_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_members ADD CONSTRAINT fk_blog_members_users FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE blog_tags ADD CONSTRAINT fk_blog_tags_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id) ON DELETE CASCADE;
ALTER TABLE blog_tags ADD CONSTRAINT fk_blog_tags_tags FOREIGN KEY (tag_id) REFERENCES tags (id);
ALTER TABLE files ADD CONSTRAINT fk_files_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE FULLTEXT INDEX idx_blogs_name_description ON blogs (name, description) WITH PARSER ngram;
CREATE INDEX idx_blogs_visibility_status_created_at ON blogs (visibility ASC, status ASC, created_at DESC);
CREATE INDEX idx_blogs_visibility_status_member_count ON blogs (visibility ASC, status ASC, member_count DESC);
CREATE INDEX idx_blogs_close_scheduled_at ON blogs (close_scheduled_at ASC);
CREATE INDEX idx_blog_members_user_id_status ON blog_members (user_id ASC, status ASC);
CREATE INDEX idx_blog_tags_tag_id ON blog_tags (tag_id ASC);
