-- 第 20 章：上传的头像文件名。为空表示回退到 Gravatar。
ALTER TABLE users ADD COLUMN avatar_file VARCHAR(80);
