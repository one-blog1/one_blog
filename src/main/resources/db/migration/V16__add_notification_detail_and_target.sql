-- 알림 자세히 보기 (3.6, D-107). ERD "One Blog 메인블로그" v7의 notifications 컬럼 추가와 같다.
-- 사유처럼 긴 내용은 목록에 보이지 않고 알림을 눌러야 보인다. 바로가기 주소는 볼 때 대상으로 계산한다.
ALTER TABLE notifications
    ADD COLUMN detail VARCHAR(1000) COMMENT '자세한 내용-----사유 등 목록에는 보이지 않고 알림을 눌러야 보이는 내용 (D-107)',
    ADD COLUMN target_type VARCHAR(20) COMMENT '대상 종류-----POST, COMMENT, BLOG. 알림 자세히 보기의 바로가기 대상 (D-107)',
    ADD COLUMN target_id BIGINT COMMENT '대상 ID-----target_type의 글·댓글·블로그 ID. 바로가기 주소는 볼 때 계산한다(글이 지워졌으면 없음)';
