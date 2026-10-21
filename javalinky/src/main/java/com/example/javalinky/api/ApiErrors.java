package com.example.javalinky.api;

import io.javalin.config.JavalinConfig;
import io.javalin.validation.ValidationException;

public final class ApiErrors {

    public static void install(JavalinConfig config) {

        // ① 校验失败 → 400 + 字段级错误明细
        config.routes.exception(ValidationException.class, (e, ctx) -> {
            ctx.status(400).json(new ErrorBody(
                "validation_failed", "请求数据校验失败", e.getErrors()));
        });

        // ② 给 API 路径补上 JSON 格式的 404 / 405
        //    （HTML 页面的错误页在 ErrorPages 里，用 "html" 内容类型区分）
        config.routes.error(404, "json", ctx ->
            ctx.json(new ErrorBody("not_found", "资源不存在", null)));
        config.routes.error(405, "json", ctx ->
            ctx.json(new ErrorBody("method_not_allowed", "这个资源不支持该方法", null)));
    }

    public record ErrorBody(String error, String message, Object details) {}

    private ApiErrors() {}
}
