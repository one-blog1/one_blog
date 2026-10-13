-- 025 메인 배너 (BRD-12, ADM-08, D-132). ERD "One Blog 메인블로그" v24의 banners, site_settings, files.purpose와 같다.
-- 광고는 관리자가 직접 넣고(banners), 인기 글은 서버가 자동으로 고른다. 인기 글 수·기간은 site_settings.
CREATE TABLE banners (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '배너 ID',
    admin_id BIGINT NOT NULL COMMENT '만든 관리자 ID',
    image_file_id BIGINT NOT NULL COMMENT '사진 파일 ID-----files.purpose = BANNER',
    title VARCHAR(100) NOT NULL COMMENT '제목',
    caption VARCHAR(100) COMMENT '작은 글씨-----광고주 이름 등. 제목 아래 작게',
    link_url VARCHAR(500) NOT NULL COMMENT '링크-----https:// 또는 http:// 주소, 또는 /로 시작하는 우리 사이트 주소',
    sort_order SMALLINT NOT NULL DEFAULT 0 COMMENT '순서-----작을수록 먼저',
    is_visible TINYINT(1) NOT NULL DEFAULT 1 COMMENT '노출 여부',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '만든 시각',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '고친 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_banners_files FOREIGN KEY (image_file_id) REFERENCES files (id) ON DELETE RESTRICT,
    CONSTRAINT fk_banners_users FOREIGN KEY (admin_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='메인 배너 광고-----메인 "내 블로그"·"메인 피드" 위 배너에 넣는 광고. 관리자가 직접 넣는다. 인기 글 배너는 자동이라 여기 없음 (BRD-12, ADM-08, D-132)';

CREATE INDEX idx_banners_sort_order_id ON banners (sort_order ASC, id ASC);

CREATE TABLE site_settings (
    setting_key VARCHAR(50) NOT NULL COMMENT '설정 이름',
    setting_value VARCHAR(200) NOT NULL COMMENT '값',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '고친 시각',
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='사이트 설정-----관리자가 고치는 사이트 전체 설정 한 줄씩. BANNER_POPULAR_COUNT(배너 인기 글 수 0~5), BANNER_POPULAR_DAYS(기간 1·7·30일) (ADM-08, D-132)';

ALTER TABLE files
    MODIFY COLUMN purpose VARCHAR(20) NOT NULL COMMENT '용도-----POST, PROFILE, BLOG_COVER, VIDEO_THUMB(글 영상 링크의 썸네일을 서버가 받은 것, D-128), BANNER(메인 배너 광고 사진, D-132)';

ALTER TABLE files DROP CHECK ck_files_purpose;

ALTER TABLE files
    ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('POST', 'PROFILE', 'BLOG_COVER', 'VIDEO_THUMB', 'BANNER'));
