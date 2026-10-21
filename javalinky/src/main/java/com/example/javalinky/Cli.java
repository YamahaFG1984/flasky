package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.service.SeedService;

public class Cli {

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(1);
        }

        AppConfig cfg = AppConfig.load();

        switch (args[0]) {
            case "migrate" -> {
                // Db.init 内部就会跑 Flyway
                Db.init(cfg.jdbcUrl(), cfg.dbUser(), cfg.dbPassword());
                System.out.println("数据库已升级到最新版本");
            }
            case "seed-roles" -> run(cfg, SeedService::insertRoles);
            case "fake" -> {
                int users = args.length > 1 ? Integer.parseInt(args[1]) : 50;
                int posts = args.length > 2 ? Integer.parseInt(args[2]) : 200;
                run(cfg, () -> SeedService.fake(users, posts));
            }
            // deploy 在第 17 章加入
            default -> {
                System.err.println("未知命令：" + args[0]);
                usage();
                System.exit(1);
            }
        }
        Db.close();
    }

    private static void run(AppConfig cfg, Runnable task) {
        Db.init(cfg.jdbcUrl(), cfg.dbUser(), cfg.dbPassword());
        var em = Db.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            SeedService.use(em);
            task.run();
            em.getTransaction().commit();
            System.out.println("完成");
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
    }

    private static void usage() {
        System.err.println("""
            用法：java -cp javalinky.jar com.example.javalinky.Cli <命令>

              migrate            跑数据库迁移
              seed-roles         创建/更新角色（可重复执行）
              fake [用户数] [文章数]  生成假数据（开发用）
            """);
    }
}
