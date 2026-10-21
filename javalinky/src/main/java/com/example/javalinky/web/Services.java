package com.example.javalinky.web;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.db.CommentRepository;
import com.example.javalinky.db.FollowRepository;
import com.example.javalinky.db.PostRepository;
import com.example.javalinky.db.RoleRepository;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.mail.MailService;
import com.example.javalinky.service.AuthService;
import io.javalin.http.Context;

/**
 * 整个应用的依赖装配表。
 * 注意：Repository 依赖“当前请求的 EntityManager”，所以不能提前创建好，
 * 要在处理器里按需造 —— 这就是下面那些工厂方法。
 * 后面每加一层（AuthService、PostRepository……）就往这里加一行。
 */
public record Services(
    AppConfig config,
    MailService mail,
    AuthService auth,
    RateLimiter loginLimiter      // 第 19 章：应用级单例，所有请求线程共用
) {

    public static Services wire(AppConfig cfg, MailService mail) {
        var loginLimiter = new RateLimiter(
            cfg.loginAttempts(),
            (double) cfg.loginAttempts() / cfg.loginWindowSeconds());
        return new Services(cfg, mail, new AuthService(cfg, mail), loginLimiter);
    }

    // ---- 请求级别的仓储（每次调用都用当前请求的 EntityManager）----
    public UserRepository users(Context ctx) { return new UserRepository(Db.em(ctx)); }
    public RoleRepository roles(Context ctx) { return new RoleRepository(Db.em(ctx)); }
    public PostRepository postRepo(Context ctx) { return new PostRepository(Db.em(ctx)); }
    public FollowRepository follows(Context ctx) { return new FollowRepository(Db.em(ctx)); }
    public CommentRepository commentRepo(Context ctx) { return new CommentRepository(Db.em(ctx)); }
}
