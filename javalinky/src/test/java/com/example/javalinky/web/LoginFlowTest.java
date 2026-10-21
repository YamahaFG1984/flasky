package com.example.javalinky.web;

import com.example.javalinky.JavalinkyApp;
import com.example.javalinky.TestFixtures;
import io.javalin.testtools.FormBody;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完整的登录流程：GET 登录页拿 CSRF 令牌和会话 Cookie → POST 登录 → 带着会话看首页。
 * 测试客户端自带 Cookie 管理，所以这里只需要手工处理 CSRF 令牌。
 */
class LoginFlowTest {

    private static final Pattern CSRF =
        Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Test
    void 登录成功后首页显示用户名() {
        var app = JavalinkyApp.create(TestFixtures.config());

        JavalinTest.test(app, (server, client) -> {
            // 准备数据：建角色和一个用户
            TestFixtures.withTransaction(em -> {
                TestFixtures.seedRoles(em);
                TestFixtures.user(em, "john");
            });

            // ① GET 登录页，拿到 CSRF 令牌（会话 Cookie 由客户端自动带上）
            var loginPage = client.get("/auth/login");
            assertThat(loginPage.headers().get("Set-Cookie").get(0))
                .contains("JAVALINKY_SESSION").contains("HttpOnly");
            String token = extractCsrf(loginPage.body().string());

            // ② POST 登录
            var body = new FormBody.Builder()
                .add("_csrf", token)
                .add("email", "john@example.com")
                .add("password", "cat12345")
                .build();
            var login = client.request("/auth/login", req -> req.post(body));

            assertThat(login.code()).isEqualTo(302);
            assertThat(login.headers().get("Location").get(0)).isEqualTo("/");

            // ③ 带着会话访问首页
            var home = client.get("/");
            assertThat(home.body().string())
                .contains("john")
                .contains("登出");
        });
    }

    @Test
    void 令牌不对就拒绝() {
        var app = JavalinkyApp.create(TestFixtures.config());

        JavalinTest.test(app, (server, client) -> {
            TestFixtures.withTransaction(em -> {
                TestFixtures.seedRoles(em);
                TestFixtures.user(em, "john");
            });

            client.get("/auth/login");          // 先建立会话
            var body = new FormBody.Builder()
                .add("_csrf", "伪造的令牌")
                .add("email", "john@example.com")
                .add("password", "cat12345")
                .build();
            assertThat(client.request("/auth/login", req -> req.post(body)).code())
                .isEqualTo(403);
        });
    }

    private static String extractCsrf(String html) {
        Matcher m = CSRF.matcher(html);
        assertThat(m.find()).as("页面里应该有 CSRF 隐藏字段").isTrue();
        return m.group(1);
    }
}
