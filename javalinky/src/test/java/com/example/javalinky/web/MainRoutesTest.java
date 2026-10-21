package com.example.javalinky.web;

import com.example.javalinky.JavalinkyApp;
import com.example.javalinky.TestFixtures;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MainRoutesTest {

    @Test
    void 匿名访客能看到首页() {
        var app = JavalinkyApp.create(TestFixtures.config());

        JavalinTest.test(app, (server, client) -> {
            var response = client.get("/");
            assertThat(response.code()).isEqualTo(200);

            String html = response.body().string();
            assertThat(html).contains("欢迎来到 Javalinky");   // 未登录时的问候语
            assertThat(html).doesNotContain("登出");           // 不该有登出按钮
            assertThat(html).contains("登录").contains("注册");
        });
    }

    @Test
    void 未登录访问编辑资料页会被重定向到登录页() {
        var app = JavalinkyApp.create(TestFixtures.config());

        JavalinTest.test(app, (server, client) -> {
            // 浏览器会带 Accept: text/html → 跳登录页；API 客户端则是 401
            var response = client.get("/edit-profile", b -> b.header("Accept", "text/html"));
            // 测试客户端默认不跟随重定向，所以能直接断言 302
            assertThat(response.code()).isEqualTo(302);
            assertThat(response.headers().get("Location").get(0)).contains("/auth/login");

            var asApi = client.get("/edit-profile", b -> b.header("Accept", "application/json"));
            assertThat(asApi.code()).isEqualTo(401);
        });
    }

    @Test
    void 不存在的页面返回404页面() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            var response = client.get("/这个页面不存在", b -> b.header("Accept", "text/html"));
            assertThat(response.code()).isEqualTo(404);
            assertThat(response.body().string()).contains("这个页面不存在");
        });
    }

    @Test
    void 用户不存在时主页返回404() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            assertThat(client.get("/user/nobody").code()).isEqualTo(404);
        });
    }
}
