package com.example.javalinky.web;

import io.javalin.config.JavalinConfig;

/**
 * 浏览器来的请求（Accept: text/html）渲染好看的错误页；
 * API 客户端（Accept: application/json）走 Javalin 默认的 JSON 错误体，一行都不用写。
 */
public final class ErrorPages {

    public static void install(JavalinConfig config) {
        config.routes.error(404, "html", ctx ->
            View.render(ctx, "error/404.jte", "页面不存在"));
        config.routes.error(403, "html", ctx ->
            View.render(ctx, "error/403.jte", "没有权限"));
        config.routes.error(500, "html", ctx ->
            View.render(ctx, "error/500.jte", "服务器错误"));
    }

    private ErrorPages() {}
}
