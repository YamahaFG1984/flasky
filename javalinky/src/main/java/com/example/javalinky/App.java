package com.example.javalinky;

import io.javalin.Javalin;
import io.javalin.http.NotFoundResponse;

public class App {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));

        Javalin.create(config -> {
            // --- 开发期辅助（第 7 章会按环境开关） ---
            config.bundledPlugins.enableDevLogging();
            config.bundledPlugins.enableRouteOverview("/routes");

            // --- 钩子 ---
            config.routes.before(ctx ->
                ctx.attribute("startedAt", System.nanoTime()));

            config.routes.after(ctx -> {
                long started = ctx.attribute("startedAt");
                long ms = (System.nanoTime() - started) / 1_000_000;
                ctx.header("X-Response-Time", ms + "ms");
            });

            // --- 路由 ---
            config.routes.get("/", ctx -> ctx.html("<h1>Hello World!</h1>"));

            config.routes.get("/user/{name}", ctx -> {
                String name = ctx.pathParam("name");
                if (name.isBlank()) throw new NotFoundResponse();
                ctx.html("<h1>Hello, " + name + "!</h1>");
            });

            config.routes.get("/whoami", ctx ->
                ctx.html("<p>你的浏览器是 " + ctx.header("User-Agent") + "</p>"));

        }).start(port);
    }
}
