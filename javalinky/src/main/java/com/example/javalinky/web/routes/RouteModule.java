package com.example.javalinky.web.routes;

import io.javalin.config.JavalinConfig;

/** 一组相关的路由。相当于 Flask 的一个蓝本。 */
public interface RouteModule {
    void register(JavalinConfig config);
}
