-- 024 글 목록 영상 썸네일 (BRD-02, BRD-05, 6.3, D-128). ERD "One Blog 메인블로그" v21의 files.purpose, ck_files_purpose와 같다.
-- 글 본문의 첫 미디어가 영상 링크(유튜브·치지직·SOOP)면 서버가 썸네일을 받아 VIDEO_THUMB로 그 글에 연결해 둔다.
ALTER TABLE files
    MODIFY COLUMN purpose VARCHAR(20) NOT NULL COMMENT '용도-----POST, PROFILE, BLOG_COVER, VIDEO_THUMB(글 영상 링크의 썸네일을 서버가 받은 것, D-128)',
    MODIFY COLUMN original_name VARCHAR(255) NOT NULL COMMENT '원래 이름-----올린 파일의 원래 이름. VIDEO_THUMB는 영상 구분(youtube:번호 등)';

ALTER TABLE files DROP CHECK ck_files_purpose;

ALTER TABLE files
    ADD CONSTRAINT ck_files_purpose CHECK (purpose IN ('POST', 'PROFILE', 'BLOG_COVER', 'VIDEO_THUMB'));
