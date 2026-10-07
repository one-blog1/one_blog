-- 009 팔로우·블로그 구독 (SOC-01~03, D-02, D-50)
-- Crowfoot ERD "One Blog 메인블로그"의 follows, blog_subscriptions를 그대로 가져온다.

CREATE TABLE follows (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    follower_id BIGINT NOT NULL COMMENT '팔로우한 회원 ID',
    followee_id BIGINT NOT NULL COMMENT '팔로우받은 회원 ID-----FK 없음 (테이블 사이 관계는 하나만)',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '팔로우 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_follows_follower_id_followee_id UNIQUE (follower_id, followee_id),
    CONSTRAINT ck_follows_self CHECK (follower_id <> followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='팔로우';

CREATE TABLE blog_subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '구독한 회원 ID',
    blog_id BIGINT NOT NULL COMMENT '블로그 ID',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '구독 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_blog_subscriptions_blog_id_user_id UNIQUE (blog_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='블로그 구독';

ALTER TABLE follows ADD CONSTRAINT fk_follows_users FOREIGN KEY (follower_id) REFERENCES users (id);
ALTER TABLE blog_subscriptions ADD CONSTRAINT fk_blog_subscriptions_blogs FOREIGN KEY (blog_id) REFERENCES blogs (id);
ALTER TABLE blog_subscriptions ADD CONSTRAINT fk_blog_subscriptions_users FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_follows_followee_id ON follows (followee_id ASC);
CREATE INDEX idx_blog_subscriptions_user_id ON blog_subscriptions (user_id ASC);
