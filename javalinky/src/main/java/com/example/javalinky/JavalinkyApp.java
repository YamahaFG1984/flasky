package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.db.UnitOfWork;
import com.example.javalinky.mail.MailService;
import com.example.javalinky.mail.Mailer;
import com.example.javalinky.web.*;
import com.example.javalinky.web.routes.*;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import gg.jte.TemplateEngine;
import io.javalin.json.JavalinJackson;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;

/** 应用工厂：给一份配置，返回一个配好但还没启动的 Javalin 实例。 */
public final class JavalinkyApp {

    public static Javalin create(AppConfig cfg) {

        // ---------- 1. 基础设施 ----------
        Db.init(cfg.jdbcUrl(), cfg.dbUser(), cfg.dbPassword());

        TemplateEngine templates = cfg.templateHotReload()
            ? Templates.directory()      // 开发：从 src/main/jte 读，改了就生效
            : Templates.precompiled();   // 生产：读 jar 里预编译好的模板类
        TemplateEngine textTemplates = cfg.templateHotReload()
            ? Templates.textDirectory()
            : Templates.textPrecompiled();

        Mailer mailer = new Mailer(cfg.mail());
        MailService mail = new MailService(
            mailer, cfg.siteBaseUrl(), templates, textTemplates);

        // ---------- 2. 业务装配 ----------
        Services services = Services.wire(cfg, mail);

        // ---------- 3. Web ----------
        return Javalin.create(javalin -> {

            javalin.fileRenderer(new JavalinJte(templates));

            javalin.jsonMapper(new JavalinJackson().updateMapper(mapper -> {
                mapper.registerModule(new JavaTimeModule());
                // Instant 序列化成 ISO-8601 字符串（"2026-10-21T08:30:00Z"）
                // 而不是 epoch 秒的小数
                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }));

            javalin.staticFiles.add(f -> {
                f.directory = "/public";
                f.location = Location.CLASSPATH;
                f.hostedPath = "/";
            });

            javalin.jetty.modifyServletContextHandler(h ->
                h.setSessionHandler(Sessions.handler(cfg.httpsOnly())));

            javalin.concurrency.useVirtualThreads = true;

            // 过滤器（顺序有讲究，见下面的说明）
            Timing.install(javalin, cfg);   // 最先装：它要量整个请求的耗时
            UnitOfWork.install(javalin);
            Csrf.install(javalin);
            CurrentUser.install(javalin);   // 必须在 UnitOfWork 之后：它要用 EntityManager
            Authorization.install(javalin); // beforeMatched：按路由上声明的角色检查权限

            // 按配置开关开发辅助
            if (cfg.devLogging())    javalin.bundledPlugins.enableDevLogging();
            if (cfg.routeOverview()) javalin.bundledPlugins.enableRouteOverview("/routes");
            if (cfg.httpsOnly())     javalin.bundledPlugins.enableSslRedirects();

            // 错误页
            ErrorPages.install(javalin);

            // 路由模块（相当于 Flask 的蓝本）
            new MainRoutes(cfg, services).register(javalin);
            new AuthRoutes(cfg, services).register(javalin);
            new ApiRoutes(cfg, services).register(javalin);

            // 优雅关闭
            javalin.events.serverStopping(() -> {
                mailer.close();
                Db.close();
            });
        });
    }

    private JavalinkyApp() {}
}
