package com.example.javalinky.db;

import io.javalin.http.Context;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.flywaydb.core.Flyway;

import java.util.HashMap;
import java.util.Map;

public final class Db {

    public static final String CTX_KEY = "entityManager";

    private static EntityManagerFactory emf;

    /** 应用启动时调一次：先跑迁移，再建 EntityManagerFactory */
    public static void init(String jdbcUrl, String user, String password) {
        // 1. Flyway 先把表结构升到最新
        Flyway.configure()
              .dataSource(jdbcUrl, user, password)
              .locations("classpath:db/migration")
              .load()
              .migrate();

        // 2. 再创建 EMF（此时 hbm2ddl=validate 的校验才会通过）
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("jakarta.persistence.jdbc.url", jdbcUrl);
        overrides.put("jakarta.persistence.jdbc.user", user);
        overrides.put("jakarta.persistence.jdbc.password", password);
        emf = Persistence.createEntityManagerFactory("javalinky", overrides);
    }

    public static EntityManagerFactory emf() {
        if (emf == null) throw new IllegalStateException("Db.init() 还没调用");
        return emf;
    }

    /** 取当前请求的 EntityManager（由过滤器放进 Context） */
    public static EntityManager em(Context ctx) {
        EntityManager em = ctx.attribute(CTX_KEY);
        if (em == null) throw new IllegalStateException("当前请求没有 EntityManager");
        return em;
    }

    public static void close() {
        if (emf != null) emf.close();
    }

    private Db() {}
}
