package com.example.javalinky.web;

import com.example.javalinky.db.Db;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.model.User;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;

import java.time.Duration;
import java.time.Instant;

public final class CurrentUser {

    private static final String SESSION_KEY = "userId";
    private static final String CTX_KEY = "currentUser";

    /** 最后活跃时间的最小更新间隔。每个请求都写一次库太浪费 */
    private static final Duration PING_INTERVAL = Duration.ofMinutes(5);

    /** 装一个 before 过滤器，把当前用户加载进每个请求 */
    public static void install(JavalinConfig config) {
        config.routes.before(ctx -> {
            Long userId = ctx.sessionAttribute(SESSION_KEY);
            if (userId == null) return;                 // 未登录，留 null

            User user = new UserRepository(Db.em(ctx)).findById(userId).orElse(null);
            if (user == null) {
                // 用户被删了，但会话还在 → 清掉会话
                logout(ctx);
                return;
            }
            ctx.attribute(CTX_KEY, user);
            ping(user);
        });
    }

    private static void ping(User user) {
        Instant now = Instant.now();
        Instant last = user.getLastSeen();
        if (last == null || Duration.between(last, now).compareTo(PING_INTERVAL) >= 0) {
            user.setLastSeen(now);   // 托管对象，改字段 = 提交时 UPDATE
        }
    }

    /** 当前用户，未登录返回 null */
    public static User get(Context ctx) {
        return ctx.attribute(CTX_KEY);
    }

    public static boolean isAuthenticated(Context ctx) {
        return get(ctx) != null;
    }

    /** 必须登录，否则抛 401（第 9 章会把它换成更好的机制） */
    public static User require(Context ctx) {
        User user = get(ctx);
        if (user == null) throw new UnauthorizedResponse("请先登录");
        return user;
    }

    public static void login(Context ctx, User user) {
        // 关键一步：换一个新的会话 ID，防止"会话固定攻击"
        ctx.req().changeSessionId();
        ctx.sessionAttribute(SESSION_KEY, user.getId());
        ctx.attribute(CTX_KEY, user);
    }

    public static void logout(Context ctx) {
        ctx.sessionAttribute(SESSION_KEY, null);
        ctx.attribute(CTX_KEY, null);
        ctx.req().getSession().invalidate();   // 整个会话作废，连 Flash 也清掉
    }

    private CurrentUser() {}
}
