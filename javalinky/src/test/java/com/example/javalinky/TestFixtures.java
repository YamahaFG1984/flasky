package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.config.Profile;
import com.example.javalinky.db.Db;
import com.example.javalinky.mail.MailConfig;
import com.example.javalinky.model.*;
import com.example.javalinky.service.SeedService;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/** 测试用的配置和数据构造器 */
public final class TestFixtures {

    /** 每次调用都给出一个全新的内存数据库 */
    public static AppConfig config() {
        return new AppConfig(
            Profile.TEST,
            0,                                  // 端口 0 = 让系统分配
            "http://localhost",
            "jdbc:h2:mem:test-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa", "",
            "test-secret-key-at-least-32-chars-long",
            false,
            MailConfig.disabled(),              // 测试不发真邮件
            "admin@example.com",
            20, 50, 30,
            Long.MAX_VALUE,
            false, false, true);
    }

    /** 在给定的 EntityManager 里建好角色 */
    public static void seedRoles(EntityManager em) {
        SeedService.use(em);
        SeedService.insertRoles();
    }

    /** 建一个已确认的普通用户，密码统一是 "cat12345" */
    public static User user(EntityManager em, String username) {
        var roles = new com.example.javalinky.db.RoleRepository(em);
        var user = new User(username + "@example.com", username);
        user.setPassword("cat12345");
        user.setConfirmed(true);
        user.setRole(roles.defaultRole());
        em.persist(user);
        return user;
    }

    /** 建一个管理员 */
    public static User admin(EntityManager em, String username) {
        var user = user(em, username);
        user.setRole(new com.example.javalinky.db.RoleRepository(em)
            .findByName("管理员").orElseThrow());
        return user;
    }

    /** 在一个独立事务里准备数据（给集成测试用） */
    public static void withTransaction(java.util.function.Consumer<EntityManager> work) {
        EntityManager em = Db.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            work.accept(em);
            em.getTransaction().commit();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    private TestFixtures() {}
}
