package com.example.javalinky.web;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.QueryStats;
import io.javalin.config.JavalinConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Timing {

    private static final Logger log = LoggerFactory.getLogger(Timing.class);
    private static final String START = "requestStartNanos";

    public static void install(JavalinConfig config, AppConfig cfg) {

        config.routes.before(ctx -> {
            ctx.attribute(START, System.nanoTime());
            QueryStats.start();
        });

        config.routes.after(ctx -> {
            Long start = ctx.attribute(START);
            if (start == null) return;

            long totalMs = (System.nanoTime() - start) / 1_000_000;
            var stats = QueryStats.stop();
            long dbMs = stats.nanos / 1_000_000;

            // ① 写进响应头，开发者工具能直接看到
            ctx.header("Server-Timing", String.format(
                "total;dur=%d, db;dur=%d, queries;desc=\"%d\"",
                totalMs, dbMs, stats.count));

            // ② 慢请求写日志
            if (totalMs >= cfg.slowQueryMillis()) {
                log.warn("慢请求 {} {} 共 {}ms，其中数据库 {}ms（{} 条查询）。最慢的一条：{}",
                    ctx.method(), ctx.path(), totalMs, dbMs, stats.count, stats.slowest);
            }

            // ③ 查询条数异常多 → 很可能是 N+1
            if (stats.count > 20) {
                log.warn("疑似 N+1：{} {} 发了 {} 条 SQL",
                    ctx.method(), ctx.path(), stats.count);
            }
        });
    }

    private Timing() {}
}
