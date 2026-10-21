package com.example.javalinky;

import com.example.javalinky.web.UserPage;
import com.example.javalinky.web.View;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;

public class App {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        Javalin.create(config -> {

            // --- 模板引擎 ---
            config.fileRenderer(new JavalinJte(JavalinJte.Companion.directoryTemplateEngine()));

            // --- 静态文件 ---
            config.staticFiles.add(f -> {
                f.directory = "/public";
                f.location = Location.CLASSPATH;
                f.hostedPath = "/";
            });

            // --- 开发期辅助 ---
            config.bundledPlugins.enableDevLogging();
            config.bundledPlugins.enableRouteOverview("/routes");

            // --- 自定义错误页 ---
            config.routes.error(404, "html", ctx ->
                View.render(ctx, "error/404.jte", "页面不存在"));
            config.routes.error(403, "html", ctx ->
                View.render(ctx, "error/403.jte", "没有权限"));
            config.routes.error(500, "html", ctx ->
                View.render(ctx, "error/500.jte", "服务器错误"));

            // --- 路由 ---
            config.routes.get("/", ctx ->
                View.render(ctx, "index.jte", "首页"));

            config.routes.get("/user/{name}", ctx -> {
                String name = ctx.pathParam("name");
                View.render(ctx, "user.jte", name, "page", new UserPage(name, 3));
            });

        }).start(port);
    }
}
