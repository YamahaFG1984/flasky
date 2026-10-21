CREATE TABLE follows (
    follower_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    followed_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    timestamp   TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    -- 复合主键：保证"同一对关系"只能有一条记录
    PRIMARY KEY (follower_id, followed_id)
);

-- 主键索引已经覆盖了"我关注了谁"的查询（最左前缀是 follower_id）
-- 但"谁关注了我"需要另一个索引
CREATE INDEX ix_follows_followed ON follows (followed_id);
