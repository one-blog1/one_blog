-- 프로필 공개 범위 설정 (SOC-03, D-114). ERD "One Blog 메인블로그" v8의 users 컬럼 추가와 같다.
ALTER TABLE users
    ADD COLUMN show_blogs_on_profile TINYINT(1) NOT NULL DEFAULT 1 COMMENT '프로필에 블로그 공개-----false면 다른 사람에게 프로필의 운영·참여 블로그 목록을 숨김 (D-114)',
    ADD COLUMN show_follows_on_profile TINYINT(1) NOT NULL DEFAULT 1 COMMENT '팔로워 목록 공개-----false면 다른 사람에게 팔로워·팔로잉 목록을 숨김(수는 보임) (D-114)',
    ADD COLUMN allow_follow TINYINT(1) NOT NULL DEFAULT 1 COMMENT '팔로우 허용-----false면 새 팔로우를 받지 않음. 이미 한 팔로우는 그대로 (D-114)';
