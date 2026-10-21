package com.example.javalinky;

import com.example.javalinky.web.*;
import com.example.javalinky.web.form.LoginForm;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;

import java.util.HashMap;

public class App {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        Javalin.create(config -> {

            config.fileRenderer(new JavalinJte(JavalinJte.Companion.directoryTemplateEngine()));

            config.staticFiles.add(f -> {
                f.directory = "/public";
                f.location = Location.CLASSPATH;
                f.hostedPath = "/";
            });

            config.jetty.modifyServletContextHandler(h ->
                h.setSessionHandler(Sessions.handler(false)));

            Csrf.install(config);

            config.bundledPlugins.enableDevLogging();

            config.routes.error(404, "html", ctx ->
                View.render(ctx, "error/404.jte", "页面不存在"));
            config.routes.error(403, "html", ctx ->
                View.render(ctx, "error/403.jte", "没有权限"));
            config.routes.error(500, "html", ctx ->
                View.render(ctx, "error/500.jte", "服务器错误"));

            config.routes.get(Routes.INDEX, ctx ->
                View.render(ctx, "index.jte", "首页"));

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
