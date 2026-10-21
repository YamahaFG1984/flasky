package com.example.javalinky.web;

import io.javalin.config.JavalinConfig;
import org.slf4j.MDC;
import java.util.UUID;

public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    public static void install(JavalinConfig config) {
        config.routes.before(ctx -> {
            // 如果上游代理已经给了一个 ID 就沿用（便于跨服务追踪）
            String id = ctx.header(HEADER);
            if (id == null || id.isBlank() || id.length() > 64) {
                id = UUID.randomUUID().toString().substring(0, 8);
            }
            MDC.put(MDC_KEY, id);
            ctx.header(HEADER, id);      // 回给客户端，用户报错时能提供这个 ID
        });

        // 必须清理！线程是复用的，不清会把 ID 带到下一个请求里
        config.routes.after(ctx -> MDC.remove(MDC_KEY));
    }

    private RequestId() {}
}
