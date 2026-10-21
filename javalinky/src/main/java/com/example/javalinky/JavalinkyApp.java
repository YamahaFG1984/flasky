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
import org.eclipse.jetty.server.ForwardedRequestCustomizer;

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

            // 只在明确知道自己跑在代理后面时才开：否则客户端可以伪造 X-Forwarded-For
            if (cfg.behindProxy()) {
                javalin.jetty.modifyHttpConfiguration(httpConfig ->
                    httpConfig.addCustomizer(new ForwardedRequestCustomizer()));
            }

            // 过滤器（顺序有讲究，见下面的说明）
            // 顺序：Timing 先装，RequestId 后装。
            // Javalin 按注册顺序跑 after 过滤器，RequestId 的 after 会清掉 MDC，
            // 所以它必须排在 Timing 的 after 之后，否则慢请求日志里的 requestId 是空的。
            Timing.install(javalin, cfg);   // 量整个请求的耗时
            RequestId.install(javalin);     // 把请求 id 放进 MDC 和响应头
            UnitOfWork.install(javalin);
            Csrf.install(javalin);
            CurrentUser.install(javalin);   // 必须在 UnitOfWork 之后：它要用 EntityManager
            Authorization.install(javalin); // beforeMatched：按路由上声明的角色检查权限

            // 按配置开关开发辅助
            if (cfg.devLogging())    javalin.bundledPlugins.enableDevLogging();
            if (cfg.routeOverview()) javalin.bundledPlugins.enableRouteOverview("/routes");
            // 把 http:// 的请求 301 到 https://
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
