package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;

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
            // seed-roles / fake / deploy 分别在第 9、11、17 章加入
            default -> {
                System.err.println("未知命令：" + args[0]);
                usage();
                System.exit(1);
            }
        }
        Db.close();
    }

    private static void usage() {
        System.err.println("""
            用法：java -cp javalinky.jar com.example.javalinky.Cli <命令>

              migrate            跑数据库迁移
            """);
    }
}
