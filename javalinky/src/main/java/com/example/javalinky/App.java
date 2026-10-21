package com.example.javalinky;

import com.example.javalinky.db.Db;
import com.example.javalinky.db.UnitOfWork;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.model.User;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.LoginForm;
import com.example.javalinky.web.form.NameForm;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;

import java.util.HashMap;
import java.util.Map;

public class App {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        // 1. 数据库：Flyway 迁移 + EntityManagerFactory
        Db.init(
            System.getenv().getOrDefault("DATABASE_URL",
                "jdbc:h2:./data/javalinky-dev;MODE=PostgreSQL"),
            System.getenv().getOrDefault("DATABASE_USER", "sa"),
            System.getenv().getOrDefault("DATABASE_PASSWORD", ""));
        Runtime.getRuntime().addShutdownHook(new Thread(Db::close));

        // 2. Web
        Javalin.create(config -> {
            config.fileRenderer(new JavalinJte(JavalinJte.Companion.directoryTemplateEngine()));
            config.staticFiles.add(f -> {
                f.directory = "/public";
                f.location = Location.CLASSPATH;
                f.hostedPath = "/";
            });
            config.jetty.modifyServletContextHandler(h ->
                h.setSessionHandler(Sessions.handler(false)));

            // 注意顺序：事务过滤器要在 CSRF 之前装，
            // 这样即使 CSRF 校验失败抛异常，after 过滤器也能正确回滚
            UnitOfWork.install(config);
            Csrf.install(config);

            config.bundledPlugins.enableDevLogging();

            config.routes.error(404, "html", ctx ->
                View.render(ctx, "error/404.jte", "页面不存在"));
            config.routes.error(403, "html", ctx ->
                View.render(ctx, "error/403.jte", "没有权限"));
            config.routes.error(500, "html", ctx ->
                View.render(ctx, "error/500.jte", "服务器错误"));

            // --- 首页：填个名字，存进数据库（第 7 章会把路由拆到独立类里） ---
            config.routes.get(Routes.INDEX, ctx -> {
                String name = ctx.sessionAttribute("name");
                Boolean known = ctx.sessionAttribute("known");
                View.render(ctx, "index.jte", "首页",
                    "form", FormView.empty(),
                    "page", new IndexPage(name, Boolean.TRUE.equals(known)));
            });

            config.routes.post(Routes.INDEX, ctx -> {
                var form = new NameForm(ctx.formParam("name"));
                var errors = Forms.validate(form);
                if (!errors.isEmpty()) {
                    View.render(ctx, "index.jte", "首页",
                        "form", new FormView(
                            Map.of("name", form.name() == null ? "" : form.name()), errors),
                        "page", new IndexPage(null, false));
                    return;
                }

                var users = new UserRepository(Db.em(ctx));
                boolean known = users.findByUsername(form.name()).isPresent();
                if (!known) {
                    users.save(new User(form.name() + "@example.com", form.name()));
                    // 不需要手动 commit —— UnitOfWork 的 after 过滤器会在请求结束时提交
                }

                ctx.sessionAttribute("name", form.name());
                ctx.sessionAttribute("known", known);
                ctx.redirect(Routes.INDEX);     // Post/Redirect/Get
            });

            // --- 登录表单（第 8 章会接上真正的认证） ---
            config.routes.get(Routes.LOGIN, ctx ->
                View.render(ctx, "auth/login.jte", "登录", "form", FormView.empty()));

            config.routes.post(Routes.LOGIN, ctx -> {
                var form = new LoginForm(
                    ctx.formParam("email"),
                    ctx.formParam("password"),
                    ctx.formParam("remember") != null);

                var errors = Forms.validate(form);
                if (!errors.isEmpty()) {
                    var values = new HashMap<String, String>();
                    values.put("email", form.email() == null ? "" : form.email());
                    if (form.remember()) values.put("remember", "1");
                    View.render(ctx, "auth/login.jte", "登录",
                                "form", new FormView(values, errors));
                    return;
                }

                Flash.success(ctx, "表单校验通过！（真正的登录在第 8 章）");
                ctx.redirect(Routes.INDEX);
            });

        }).start(port);
    }
}
