package com.example.javalinky.config;

import com.example.javalinky.mail.MailConfig;

public record AppConfig(
    Profile profile,
    int port,
    String siteBaseUrl,

    // 数据库
    String jdbcUrl,
    String dbUser,
    String dbPassword,

    // 安全
    String secretKey,          // 签发令牌用（第 8 章）
    boolean httpsOnly,         // 会话 Cookie 是否只在 HTTPS 下发送
    boolean behindProxy,       // 是否跑在 nginx/负载均衡后面（信任 X-Forwarded-*）

    // 邮件
    MailConfig mail,
    String adminEmail,

    // 分页（原书的 FLASKY_*_PER_PAGE）
    int postsPerPage,
    int followersPerPage,
    int commentsPerPage,

    // 性能（第 16 章）
    long slowQueryMillis,

    // 限流（第 19 章）：每个 IP 在 loginWindowSeconds 内最多尝试登录多少次
    int loginAttempts,
    int loginWindowSeconds,

    // 上传（第 20 章）：头像存在哪
    String avatarDir,

    // 实时（第 21 章）：SSE 心跳间隔（毫秒）
    long sseHeartbeatMillis,

    // 开发辅助
    boolean devLogging,
    boolean routeOverview,
    boolean templateHotReload
) {

    public static AppConfig load() {
        Env env = Env.load();
        Profile profile = Profile.fromEnv(env.get("APP_PROFILE", "dev"));
        return switch (profile) {
            case DEV  -> dev(env);
            case TEST -> test(env);
            case PROD -> prod(env);
        };
    }

    // ---------- 开发 ----------
    private static AppConfig dev(Env env) {
        return new AppConfig(
            Profile.DEV,
            env.getInt("PORT", 7070),
            env.get("SITE_BASE_URL", "http://localhost:7070"),
            env.get("DATABASE_URL", "jdbc:h2:./data/javalinky-dev;MODE=PostgreSQL"),
            env.get("DATABASE_USER", "sa"),
            env.get("DATABASE_PASSWORD", ""),
            env.get("SECRET_KEY", "dev-only-insecure-key-change-in-prod"),
            false, false,
            MailConfig.fromEnv(),
            env.get("ADMIN_EMAIL", "admin@example.com"),
            20, 50, 30,
            500,
            env.getInt("LOGIN_ATTEMPTS", 10), env.getInt("LOGIN_WINDOW_SECONDS", 300),
            env.get("AVATAR_DIR", "data/avatars"),
            env.getInt("SSE_HEARTBEAT_MILLIS", 20_000),
            true, true, true
        );
    }

    // ---------- 测试 ----------
    private static AppConfig test(Env env) {
        return new AppConfig(
            Profile.TEST,
            0,                       // 0 = 让系统随便分配一个空闲端口
            "http://localhost",
            // 每次测试一个全新的内存库，互不干扰。
            // DB_CLOSE_DELAY=-1 必不可少：否则 Flyway 迁移完关掉连接，
            // 整个内存库就被丢掉了，Hibernate 再连上去会发现“表不存在”
            "jdbc:h2:mem:test-" + java.util.UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa", "",
            "test-secret-key",
            false, false,
            MailConfig.disabled(),   // 测试时不真的发邮件
            "admin@example.com",
            20, 50, 30,
            Long.MAX_VALUE,          // 测试里不报慢查询
            1000, 60,                // 限流在测试里形同虚设，免得干扰别的用例
            env.get("AVATAR_DIR", "target/test-avatars"),
            env.getInt("SSE_HEARTBEAT_MILLIS", 300),   // 测试里调小，好验证清理
            false, false, true
        );
    }

    // ---------- 生产 ----------
    private static AppConfig prod(Env env) {
        return new AppConfig(
            Profile.PROD,
            env.getInt("PORT", 8080),
            env.require("SITE_BASE_URL"),      // ← 生产必须显式给
            env.require("DATABASE_URL"),
            env.require("DATABASE_USER"),
            env.require("DATABASE_PASSWORD"),
            env.require("SECRET_KEY"),         // ← 绝不能用默认值
            env.getBoolean("HTTPS_ONLY", true),
            env.getBoolean("BEHIND_PROXY", false),
            MailConfig.fromEnv(),
            env.require("ADMIN_EMAIL"),
            env.getInt("POSTS_PER_PAGE", 20),
            env.getInt("FOLLOWERS_PER_PAGE", 50),
            env.getInt("COMMENTS_PER_PAGE", 30),
            env.getInt("SLOW_QUERY_MILLIS", 500),
            env.getInt("LOGIN_ATTEMPTS", 5), env.getInt("LOGIN_WINDOW_SECONDS", 300),
            env.require("AVATAR_DIR"),      // ← 生产必须显式给，别写在容器的临时层里
            env.getInt("SSE_HEARTBEAT_MILLIS", 20_000),
            false, false, false                // 生产环境关掉所有开发辅助
        );
    }
}
