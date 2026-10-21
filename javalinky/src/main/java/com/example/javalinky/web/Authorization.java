package com.example.javalinky.web;

import com.example.javalinky.model.Permission;
import com.example.javalinky.model.User;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.security.RouteRole;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class Authorization {

    public static void install(JavalinConfig config) {
        config.routes.beforeMatched(Authorization::check);
    }

    private static void check(Context ctx) {
        Set<? extends RouteRole> required = ctx.routeRoles();

        // 1. 没声明角色，或者显式声明公开 → 放过
        if (required.isEmpty() || required.contains(Access.PUBLIC)) return;

        User user = CurrentUser.get(ctx);

        // 2. 需要身份但没登录
        if (user == null) {
            denyAnonymous(ctx);
            return;
        }

        // 3. 逐个检查权限要求（声明了多个就必须全部满足）
        for (RouteRole role : required) {
            if (role instanceof Permission permission && !user.can(permission)) {
                throw new ForbiddenResponse("你没有执行这个操作的权限");
            }
        }
        // Access.AUTHENTICATED 到这里已经满足了（user != null）
    }

    /**
     * 未登录时的处理要分两种客户端：
     *   浏览器 → 跳到登录页，并记住他原本想去哪
     *   API    → 返回 401，让客户端自己处理
     */
    private static void denyAnonymous(Context ctx) {
        boolean wantsHtml = ctx.header("Accept") != null
            && ctx.header("Accept").contains("text/html");

        if (!wantsHtml || ctx.path().startsWith("/api/")) {
            throw new UnauthorizedResponse("请先登录");
        }

        Flash.warning(ctx, "请先登录。");
        String next = URLEncoder.encode(ctx.fullUrl(), StandardCharsets.UTF_8);
        ctx.redirect(Routes.LOGIN + "?next=" + next);
        ctx.skipRemainingHandlers();
    }

    private Authorization() {}
}
