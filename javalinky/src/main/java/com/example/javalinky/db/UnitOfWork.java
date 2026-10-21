package com.example.javalinky.db;

import io.javalin.config.JavalinConfig;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class UnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(UnitOfWork.class);

    public static void install(JavalinConfig config) {

        // 请求开始：开一个 EntityManager 和事务
        config.routes.before(ctx -> {
            EntityManager em = Db.emf().createEntityManager();
            ctx.attribute(Db.CTX_KEY, em);
            em.getTransaction().begin();
        });

        // 请求结束：按状态码决定提交还是回滚，然后一定要关掉
        config.routes.after(ctx -> {
            EntityManager em = ctx.attribute(Db.CTX_KEY);
            if (em == null) return;
            try {
                var tx = em.getTransaction();
                if (!tx.isActive()) return;
                if (ctx.statusCode() >= 400) {
                    tx.rollback();
                } else {
                    tx.commit();
                }
            } catch (RuntimeException e) {
                log.error("提交事务失败", e);
                safeRollback(em);
                throw e;
            } finally {
                em.close();          // 必须关，否则连接池会耗尽
            }
        });
    }

    private static void safeRollback(EntityManager em) {
        try {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } catch (RuntimeException ignored) {
            // 回滚本身失败就只能放弃了
        }
    }

    private UnitOfWork() {}
}
