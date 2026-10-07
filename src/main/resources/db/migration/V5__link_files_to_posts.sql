-- 005 이미지 첨부·에디터 (BRD-05, 6.3, D-74, D-75)
-- 002에서 posts가 없어 미뤄 둔 files.post_id 외래 키를 ERD대로 추가한다 (docs/roadmap.md "002의 대표 이미지").

ALTER TABLE files ADD CONSTRAINT fk_files_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE SET NULL;
