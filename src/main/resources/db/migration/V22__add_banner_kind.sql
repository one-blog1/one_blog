-- 025 메인 배너 종류 늘리기 (BRD-12, ADM-08, D-133). ERD "One Blog 메인블로그" v26의 banners.kind, ck_banners_kind와 같다.
-- 관리자가 넣는 배너는 광고·공지·이벤트 중 하나. 새 블로그·인기 블로그는 인기 글처럼 자동이라 테이블이 없고 개수·기간만 site_settings에 둔다.
ALTER TABLE banners
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'AD' COMMENT '종류-----AD(광고), NOTICE(공지), EVENT(이벤트). 배너의 표시 이름 (D-133)' AFTER image_file_id,
    ADD CONSTRAINT ck_banners_kind CHECK (kind IN ('AD', 'NOTICE', 'EVENT')),
    COMMENT = '메인 배너 (관리자)-----메인 "내 블로그"·"메인 피드" 위 배너에 관리자가 직접 넣는 광고·공지·이벤트. 인기 글·새 블로그·인기 블로그 배너는 자동이라 여기 없음 (BRD-12, ADM-08, D-132, D-133)';

ALTER TABLE site_settings
    COMMENT = '사이트 설정-----관리자가 고치는 사이트 전체 설정 한 줄씩. 배너 자동 종류마다 BANNER_{POPULAR|NEW_BLOG|POPULAR_BLOG}_COUNT(0~5)·_DAYS(1·7·30일) (ADM-08, D-132, D-133)';
