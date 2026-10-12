-- 메인 공지 종류 (BRD-10, D-122). ERD "One Blog 메인블로그" v11의 posts.notice_category, ck_posts_notice_category와 같다.
ALTER TABLE posts
    ADD COLUMN notice_category VARCHAR(20) NULL COMMENT '공지 종류-----메인 공지(MAIN_NOTICE)만: GENERAL 일반, UPDATE 업데이트, FIX 수정, MAINTENANCE 점검 (D-122). 다른 글은 NULL' AFTER post_type;

-- 이미 올린 메인 공지는 일반으로
UPDATE posts SET notice_category = 'GENERAL' WHERE post_type = 'MAIN_NOTICE';

ALTER TABLE posts
    ADD CONSTRAINT ck_posts_notice_category CHECK (
        (post_type = 'MAIN_NOTICE' AND notice_category IN ('GENERAL', 'UPDATE', 'FIX', 'MAINTENANCE'))
        OR (post_type <> 'MAIN_NOTICE' AND notice_category IS NULL));
