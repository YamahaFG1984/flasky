package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.LoginForm;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.HashMap;

import static io.javalin.apibuilder.ApiBuilder.*;

/** 认证相关的路由。相当于 Flask 的 auth 蓝本。第 8 章会填满它。 */
public class AuthRoutes implements RouteModule {

    private final AppConfig cfg;
    private final Services services;

    public AuthRoutes(AppConfig cfg, Services services) {
        this.cfg = cfg;
        this.services = services;
    }

    @Override
    public void register(JavalinConfig config) {
        config.routes.apiBuilder(() -> {
            // 这个 path 块里的所有路由都自动带上 /auth 前缀
            path("/auth", () -> {
                get("/login", this::loginForm);
                post("/login", this::login);
                get("/logout", this::logout);
            });
        });
    }

    private void loginForm(Context ctx) {
        View.render(ctx, "auth/login.jte", "登录", "form", FormView.empty());
    }

    private void login(Context ctx) {
        var form = new LoginForm(
            ctx.formParam("email"),
            ctx.formParam("password"),
            ctx.formParam("remember") != null);

        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            var values = new HashMap<String, String>();
            values.put("email", form.email() == null ? "" : form.email());
            if (form.remember()) values.put("remember", "1");
            View.render(ctx, "auth/login.jte", "登录", "form", new FormView(values, errors));
            return;
        }

        // 第 8 章会在这里真的查用户、验密码、写会话
        Flash.success(ctx, "表单校验通过！（真正的登录在第 8 章）");
        ctx.redirect(Routes.INDEX);
    }

    private void logout(Context ctx) {
        ctx.req().getSession().invalidate();
        Flash.info(ctx, "你已登出。");
        ctx.redirect(Routes.INDEX);
    }
}
