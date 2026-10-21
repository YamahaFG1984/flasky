package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.config.Profile;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BasicsTest {

    @Test
    void 测试配置使用内存数据库() {
        AppConfig cfg = testConfig();
        assertThat(cfg.profile()).isEqualTo(Profile.TEST);
        assertThat(cfg.jdbcUrl()).startsWith("jdbc:h2:mem:");
        assertThat(cfg.devLogging()).isFalse();
    }

    @Test
    void 首页返回200() {
        Javalin app = JavalinkyApp.create(testConfig());

        // JavalinTest 会在一个随机端口上真的启动服务器，
        // 给你一个 HTTP 客户端，测完自动关掉
        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/");
            assertThat(response.code()).isEqualTo(200);
            assertThat(response.body().string()).contains("Javalinky");
        });
    }

    @Test
    void 不存在的页面返回404() {
        JavalinTest.test(JavalinkyApp.create(testConfig()), (server, client) -> {
            assertThat(client.get("/这个页面不存在").code()).isEqualTo(404);
        });
    }

    private static AppConfig testConfig() {
        // 让 AppConfig.load() 走 TEST 分支。
        // 第 15 章会用一个更干净的办法（直接暴露一个 AppConfig.forTest() 工厂）
        return AppConfig.load();
    }
}
