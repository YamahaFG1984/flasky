ALTER TABLE users ADD COLUMN name        VARCHAR(64);
ALTER TABLE users ADD COLUMN location    VARCHAR(64);
ALTER TABLE users ADD COLUMN about_me    TEXT;
ALTER TABLE users ADD COLUMN last_seen   TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN avatar_hash VARCHAR(32);

-- 已有用户的 last_seen 先按注册时间填上，避免 null
UPDATE users SET last_seen = member_since WHERE last_seen IS NULL;
