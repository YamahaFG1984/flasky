-- 第 23 章：用户的时区和语言偏好。NULL 表示「跟浏览器走」
ALTER TABLE users ADD COLUMN time_zone VARCHAR(64);
ALTER TABLE users ADD COLUMN locale    VARCHAR(16);
