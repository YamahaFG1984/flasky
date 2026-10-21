package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

class ApiTest {

    @Test
    void 没有令牌一律401() {
        var app = JavalinkyApp.create(AppConfig.load());
        JavalinTest.test(app, (server, client) -> {
            var r = client.get("/api/v1/posts");
            assertThat(r.code()).isEqualTo(401);
        });
    }

    @Test
    void 用Basic换令牌再用Bearer访问() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String token = issueToken(client, "john@example.com", "cat12345");
            assertThat(token).isNotBlank();

            var r = client.get("/api/v1/posts", req -> req.header("Authorization", "Bearer " + token));
            assertThat(r.code()).isEqualTo(200);
            String body = r.body().string();
            assertThat(body).contains("\"items\"").contains("\"totalPages\"");
        });
    }

    @Test
    void 密码错了换不到令牌() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            var r = client.request("/api/v1/tokens", req -> req
                .header("Authorization", basic("john@example.com", "wrong"))
                .post(java.net.http.HttpRequest.BodyPublishers.noBody()));
            assertThat(r.code()).isEqualTo(401);
        });
    }

    @Test
    void 发文章返回201和Location头() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String token = issueToken(client, "john@example.com", "cat12345");

            var r = client.post("/api/v1/posts", new java.util.HashMap<String, String>() {{
                put("body", "通过 **API** 发的文章");
            }}, req -> req.header("Authorization", "Bearer " + token));

            assertThat(r.code()).isEqualTo(201);
            assertThat(r.headers().get("Location").get(0)).contains("/api/v1/posts/");

            String body = r.body().string();
            // DTO 只暴露挑好的字段
            assertThat(body).contains("<strong>API</strong>")      // bodyHtml 已渲染
                            .contains("\"authorUsername\":\"john\"")
                            .contains("\"commentsUrl\"");
            // 时间是 ISO-8601 字符串，不是 epoch 小数
            assertThat(body).containsPattern("\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T");
        });
    }

    @Test
    void 实体字段不会泄漏到JSON里() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String token = issueToken(client, "john@example.com", "cat12345");
            long id = userIdByEmail("john@example.com");

            String body = client.get("/api/v1/users/" + id,
                req -> req.header("Authorization", "Bearer " + token)).body().string();

            assertThat(body).contains("\"username\":\"john\"");
            assertThat(body).doesNotContain("passwordHash")
                            .doesNotContain("john@example.com")   // 邮箱不公开
                            .doesNotContain("\"role\"")
                            .doesNotContain("confirmed");
        });
    }

    @Test
    void 不能改别人的文章() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUser("susan@example.com", "susan", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String johnToken = issueToken(client, "john@example.com", "cat12345");
            var created = client.post("/api/v1/posts",
                java.util.Map.of("body", "john 的文章"),
                req -> req.header("Authorization", "Bearer " + johnToken));
            String location = created.headers().get("Location").get(0);
            String path = location.substring(location.indexOf("/api/v1"));

            String susanToken = issueToken(client, "susan@example.com", "cat12345");
            var r = client.request(path, req -> req
                .header("Authorization", "Bearer " + susanToken)
                .header("Content-Type", "application/json")
                .patch(java.net.http.HttpRequest.BodyPublishers.ofString("{\"body\":\"篡改\"}")));
            assertThat(r.code()).isEqualTo(403);
        });
    }

    @Test
    void 校验失败返回400和字段明细() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String token = issueToken(client, "john@example.com", "cat12345");
            var r = client.post("/api/v1/posts", java.util.Map.of("body", "  "),
                req -> req.header("Authorization", "Bearer " + token));
            assertThat(r.code()).isEqualTo(400);
            assertThat(r.body().string()).contains("validation_failed").contains("body 不能为空");
        });
    }

    @Test
    void API的404是JSON不是HTML页面() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            String token = issueToken(client, "john@example.com", "cat12345");
            var r = client.get("/api/v1/posts/999999", req -> req
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json"));
            assertThat(r.code()).isEqualTo(404);
            assertThat(r.body().string()).contains("not_found").doesNotContain("<html");
        });
    }

    // ---------- 小工具 ----------

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes());
    }

    private static String issueToken(io.javalin.testtools.HttpClient client,
                                     String email, String password) {
        var r = client.request("/api/v1/tokens", req -> req
            .header("Authorization", basic(email, password))
            .post(java.net.http.HttpRequest.BodyPublishers.noBody()));
        assertThat(r.code()).as("换令牌应该 200").isEqualTo(200);
        var m = java.util.regex.Pattern.compile("\"token\":\"([^\"]+)\"")
            .matcher(r.body().string());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static long userIdByEmail(String email) {
        final long[] holder = new long[1];
        inTransaction(em -> holder[0] = em.createQuery(
                "select u.id from User u where u.email = :e", Long.class)
            .setParameter("e", email).getSingleResult());
        return holder[0];
    }
}
