-- 011 알림 (SOC-04, 3.6, D-72)
-- Crowfoot ERD "One Blog 메인블로그"의 notifications, notification_settings를 그대로 가져온다.

CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '알림 ID',
    user_id BIGINT NOT NULL COMMENT '받는 회원 ID',
    type VARCHAR(40) NOT NULL COMMENT '알림 종류-----COMMENT, REPLY, LIKE, FOLLOW, BLOG_SUBSCRIBE, JOIN_REQUEST, JOIN_RESULT, TRANSFER_REQUEST, TRANSFER_RESULT, BLOG_CLOSING, BLOG_CLOSE_CANCELED, BLOG_PRIVATE, BLACKLIST_RESULT, POST_DELETED, REPORT_RESULT, SANCTION, OWNER_SANCTION, NOTICE',
    tab VARCHAR(20) NOT NULL COMMENT '탭-----COMMENT, LIKE, FOLLOW, BLOG, OPERATION',
    message VARCHAR(300) NOT NULL COMMENT '내용',
    link_url VARCHAR(500) COMMENT '이동 주소',
    dedupe_key VARCHAR(100) COMMENT '중복 방지 키-----예: BLOG_CLOSING:{blog_id}:D3',
    is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '읽음 여부',
    read_at DATETIME(6) COMMENT '읽은 시각',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '받은 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_user_id_dedupe_key UNIQUE (user_id, dedupe_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='알림-----30초 폴링으로 가져간다. dedupe_key로 같은 알림이 두 번 가지 않게 한다 (3.6, D-72)';

CREATE TABLE notification_settings (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    type VARCHAR(40) NOT NULL COMMENT '알림 종류',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '받기 여부',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_notification_settings_user_id_type UNIQUE (user_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='알림 설정-----끌 수 있는 알림만 행을 둔다. 행이 없으면 켜진 것으로 본다 (3.6)';

ALTER TABLE notifications ADD CONSTRAINT fk_notifications_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE notification_settings ADD CONSTRAINT fk_notification_settings_users FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

CREATE INDEX idx_notifications_user_id_is_read_created_at ON notifications (user_id ASC, is_read ASC, created_at DESC);
CREATE INDEX idx_notifications_created_at ON notifications (created_at ASC);
