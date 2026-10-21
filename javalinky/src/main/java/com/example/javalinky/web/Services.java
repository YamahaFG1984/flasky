package com.example.javalinky.web;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.mail.MailService;
import io.javalin.http.Context;

/**
 * 整个应用的依赖装配表。
 * 注意：Repository 依赖“当前请求的 EntityManager”，所以不能提前创建好，
 * 要在处理器里按需造 —— 这就是下面那些工厂方法。
 * 后面每加一层（AuthService、PostRepository……）就往这里加一行。
 */
public record Services(
    AppConfig config,
    MailService mail
) {

    public static Services wire(AppConfig cfg, MailService mail) {
        return new Services(cfg, mail);
    }

    // ---- 请求级别的仓储（每次调用都用当前请求的 EntityManager）----
    public UserRepository users(Context ctx) { return new UserRepository(Db.em(ctx)); }
}
