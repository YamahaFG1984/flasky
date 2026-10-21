package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.service.SeedService;
import com.example.javalinky.util.Passwords;
import io.javalin.testtools.FormBody;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTest {

    @Test
    void 相同密码的哈希每次都不同() {
        String h1 = Passwords.hash("cat");
        String h2 = Passwords.hash("cat");
        assertThat(h1).isNotEqualTo(h2);       // 因为盐是随机的
        assertThat(Passwords.verify("cat", h1)).isTrue();
        assertThat(Passwords.verify("cat", h2)).isTrue();
        assertThat(Passwords.verify("dog", h1)).isFalse();
    }

    @Test
    void 未登录访问受保护页面会被挡住() {
        JavalinTest.test(JavalinkyApp.create(AppConfig.load()), (server, client) -> {
            // 测试客户端默认不跟随重定向，所以能直接看到 302/401
            assertThat(client.get("/auth/change-password").code()).isIn(302, 401);
        });
    }

    @Test
    void 没有CSRF令牌的POST会被拒绝() {
        JavalinTest.test(JavalinkyApp.create(AppConfig.load()), (server, client) -> {
            Response r = postForm(client, "/auth/login", Map.of(
                "email", "john@example.com", "password", "cat12345"));
            assertThat(r.code()).isEqualTo(403);
        });
    }

    @Test
    void 注册并登录的完整流程() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();          // 注册时要给新用户分配默认角色

        JavalinTest.test(app, (server, client) -> {
            // 测试客户端自带 Cookie 管理，所以会话能跨请求保持
            var register = postForm(client, "/auth/register", form(
                "_csrf", csrfTokenFrom(client.get("/auth/register").body().string()),
                "email", "John@Example.com",
                "username", "john",
                "password", "cat12345",
                "password2", "cat12345"));
            assertThat(register.code()).isEqualTo(302);

            // 邮箱大小写不敏感：注册时存的是小写，登录时也先小写化
            var login = postForm(client, "/auth/login", form(
                "_csrf", csrfTokenFrom(client.get("/auth/login").body().string()),
                "email", "john@example.com",
                "password", "cat12345"));
            assertThat(login.code()).isEqualTo(302);

            // 登录之后，未确认的账号访问首页会被赶去 /auth/unconfirmed
            var index = client.get("/");
            assertThat(index.code()).isEqualTo(302);
            assertThat(index.headers().get("Location").get(0)).isEqualTo("/auth/unconfirmed");

            // 导航栏上应该出现用户名
            assertThat(client.get("/auth/unconfirmed").body().string()).contains("john");
        });
    }

    @Test
    void 密码错误时不区分用户是否存在() {
        JavalinTest.test(JavalinkyApp.create(AppConfig.load()), (server, client) -> {
            var r = postForm(client, "/auth/login", form(
                "_csrf", csrfTokenFrom(client.get("/auth/login").body().string()),
                "email", "nobody@example.com",
                "password", "whatever"));
            assertThat(r.code()).isEqualTo(200);                    // 原地重渲染
            assertThat(r.body().string()).contains("邮箱或密码错误");
        });
    }

    // ---------- 小工具（第 15 章会挪进 TestFixtures） ----------

    private static final Pattern CSRF =
        Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    static String csrfTokenFrom(String html) {
        var m = CSRF.matcher(html);
        assertThat(m.find()).as("页面里应该有 _csrf 隐藏域").isTrue();
        return m.group(1);
    }

    static Map<String, String> form(String... pairs) {
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    /** 灌入角色。相当于原书测试里的 Role.insert_roles()（第 15 章会挪进 TestFixtures） */
    static void seedRoles() {
        var em = Db.emf().createEntityManager();
        try {
            em.getTransaction().begin();
            SeedService.use(em);
            SeedService.insertRoles();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    static Response postForm(HttpClient client, String path, Map<String, String> fields) {
        var body = new FormBody.Builder();
        fields.forEach(body::add);
        return client.request(path, req -> req.post(body.build()));
    }
}
