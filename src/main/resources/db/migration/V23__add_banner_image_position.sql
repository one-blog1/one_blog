-- 026 배너 사진 위치 (ADM-08, D-134). ERD "One Blog 메인블로그" v28의 banners.image_pos_x·image_pos_y, ck_banners_image_pos와 같다.
-- 관리자가 미리보기를 보며 사진에서 보일 자리를 정한다(CSS object-position %). 기본은 가운데(50%, 50%).
ALTER TABLE banners
    ADD COLUMN image_pos_x TINYINT NOT NULL DEFAULT 50 COMMENT '사진 가로 위치-----사진에서 보일 자리(CSS object-position)의 가로 %, 0=왼쪽 끝, 100=오른쪽 끝 (D-134)' AFTER image_file_id,
    ADD COLUMN image_pos_y TINYINT NOT NULL DEFAULT 50 COMMENT '사진 세로 위치-----사진에서 보일 자리의 세로 %, 0=위 끝, 100=아래 끝 (D-134)' AFTER image_pos_x,
    ADD CONSTRAINT ck_banners_image_pos CHECK (image_pos_x BETWEEN 0 AND 100 AND image_pos_y BETWEEN 0 AND 100);
