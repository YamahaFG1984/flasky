package com.example.javalinky.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

public final class Csrf {

    private static final String SESSION_KEY = "csrfToken";
    private static final String FORM_FIELD = "_csrf";
    private static final String HEADER = "X-CSRF-Token";

    /** 不会改变服务器状态的方法，不需要校验 */
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 在 Javalin 配置里调一次，装上两个过滤器 */
    public static void install(JavalinConfig config) {

        // ① 每个请求：确保会话里有令牌，并暴露给模板
        config.routes.before(ctx -> ctx.attribute(SESSION_KEY, tokenFor(ctx)));

        // ② 不安全的方法：校验令牌
        config.routes.before(ctx -> {
            if (SAFE.contains(ctx.method().name())) return;
            // API 不用 Cookie 会话，而是每次带 Bearer 令牌，天然不受 CSRF 影响
            if (ctx.path().startsWith("/api/")) return;

            String sessionToken = ctx.sessionAttribute(SESSION_KEY);
            String submitted = ctx.formParam(FORM_FIELD);
            if (submitted == null) submitted = ctx.header(HEADER);   // AJAX 走请求头

            if (sessionToken == null || submitted == null || !constantTimeEquals(sessionToken, submitted)) {
                throw new ForbiddenResponse("CSRF 令牌缺失或不匹配，请刷新页面重试");
            }
        });
    }

    /** 取当前会话的令牌，没有就生成一个 */
    private static String tokenFor(Context ctx) {
        String token = ctx.sessionAttribute(SESSION_KEY);
        if (token == null) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            ctx.sessionAttribute(SESSION_KEY, token);
        }
        return token;
    }

    /**
     * 定长时间比较，避免"按字符逐个比较、不同就提前返回"泄露信息。
     * 普通的 equals() 比较越早失败返回越快，攻击者能据此一个字符一个字符地猜。
     */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    private Csrf() {}
}
