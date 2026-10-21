package com.example.javalinky;

import com.example.javalinky.model.User;
import io.javalin.testtools.FormBody;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import jakarta.persistence.EntityManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试里反复要用的 HTTP 动作。数据库夹具在 TestFixtures 里。
 *
 * 注意 javalin-testtools 的 client.post(path, body) 发的是 JSON，
 * 表单必须用 FormBody，否则服务端 ctx.formParam() 取不到值。
 */
final class TestHttp {

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

    static Response postForm(HttpClient client, String path, Map<String, String> fields) {
        var body = new FormBody.Builder();
        fields.forEach(body::add);
        return client.request(path, req -> req.post(body.build()));
    }

    /** 带上 _csrf 的表单 POST：先 GET 一个页面拿令牌 */
    static Response postFormWithCsrf(HttpClient client, String tokenPage,
                                     String path, Map<String, String> fields) {
        var all = new LinkedHashMap<String, String>();
        all.put("_csrf", csrfTokenFrom(client.get(tokenPage).body().string()));
        all.putAll(fields);
        return postForm(client, path, all);
    }

    /** 浏览器才会带 Accept: text/html —— 影响未登录时是 302 还是 401 */
    static Response getAsBrowser(HttpClient client, String path) {
        return client.get(path, req -> req.header("Accept", "text/html"));
    }

    // ---------- 数据库夹具（转调 TestFixtures） ----------

    static void inTransaction(Consumer<EntityManager> work) {
        TestFixtures.withTransaction(work);
    }

    static void seedRoles() {
        TestFixtures.withTransaction(TestFixtures::seedRoles);
    }

    /** 造一个已确认的用户，密码统一是 cat12345 */
    static void createUser(String email, String username, String roleName) {
        TestFixtures.withTransaction(em -> {
            User user = TestFixtures.user(em, username);
            user.setEmail(email.toLowerCase());
            if (!"普通用户".equals(roleName)) {
                user.setRole(new com.example.javalinky.db.RoleRepository(em)
                    .findByName(roleName).orElseThrow());
            }
        });
    }

    /** 没有角色的用户：什么权限都没有 */
    static void createUserWithoutRole(String email, String username) {
        TestFixtures.withTransaction(em -> {
            var user = new User(email.toLowerCase(), username);
            user.setPassword("cat12345");
            user.setConfirmed(true);
            em.persist(user);
        });
    }

    static void login(HttpClient client, String email) {
        var r = postFormWithCsrf(client, "/auth/login", "/auth/login",
            form("email", email, "password", "cat12345"));
        assertThat(r.code()).as("登录应该 302").isEqualTo(302);
    }

    /** 登出。导航栏里的登出表单带着 _csrf，先从首页取 */
    static void logout(HttpClient client) {
        var r = postFormWithCsrf(client, "/", "/auth/logout", Map.of());
        assertThat(r.code()).isEqualTo(302);
    }

    /** 换一个账号：先登出再登录（已登录时 GET /auth/login 会被重定向，取不到令牌） */
    static void switchUser(HttpClient client, String email) {
        logout(client);
        login(client, email);
    }

    private TestHttp() {}
}
