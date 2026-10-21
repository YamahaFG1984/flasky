package com.example.javalinky;

import com.example.javalinky.model.User;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProfileTest {

    @Test
    void 未登录访问编辑资料会被送去登录页() {
        var app = JavalinkyApp.create(TestFixtures.config());
        JavalinTest.test(app, (server, client) -> {
            var r = getAsBrowser(client, "/edit-profile");
            assertThat(r.code()).isEqualTo(302);
            assertThat(r.headers().get("Location").get(0)).startsWith("/auth/login?next=");
        });
    }

    @Test
    void 本人可以编辑自己的资料() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");

            var r = postFormWithCsrf(client, "/edit-profile", "/edit-profile",
                form("name", "John Doe", "location", "Hangzhou", "aboutMe", "你好"));
            assertThat(r.code()).isEqualTo(302);

            String profile = client.get("/user/john").body().string();
            assertThat(profile).contains("John Doe").contains("Hangzhou").contains("你好");
            // 邮箱只对管理员可见
            assertThat(profile).doesNotContain("john@example.com");
        });
    }

    @Test
    void 超长的自我介绍会被挡下() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            var r = postFormWithCsrf(client, "/edit-profile", "/edit-profile",
                form("name", "", "location", "", "aboutMe", "x".repeat(2001)));
            assertThat(r.code()).isEqualTo(200);                  // 原地重渲染
            assertThat(r.body().string()).contains("自我介绍最长 2000 个字符");
        });
    }

    @Test
    void 普通用户访问管理员页面返回403() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            long id = idOf("john");
            assertThat(getAsBrowser(client, "/admin/edit-profile/" + id).code()).isEqualTo(403);
        });
    }

    @Test
    void 管理员可以改别人的角色和确认状态() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("admin@example.com", "root", "管理员");
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "admin@example.com");
            long johnId = idOf("john");
            long moderatorRoleId = roleIdOf("协管员");

            String formPage = "/admin/edit-profile/" + johnId;
            String html = client.get(formPage).body().string();
            // 管理员页面能看到对方邮箱
            assertThat(html).contains("john@example.com");

            var r = postFormWithCsrf(client, formPage, formPage, form(
                "email", "john@example.com",
                "username", "john",
                "confirmed", "1",
                "roleId", String.valueOf(moderatorRoleId),
                "name", "John",
                "location", "",
                "aboutMe", ""));
            assertThat(r.code()).isEqualTo(302);

            // 角色真的变了
            final long[] holder = new long[1];
            inTransaction(em -> holder[0] = em.createQuery(
                    "select u.role.id from User u where u.username = 'john'", Long.class)
                .getSingleResult());
            assertThat(holder[0]).isEqualTo(moderatorRoleId);
        });
    }

    private static long idOf(String username) {
        final long[] holder = new long[1];
        inTransaction(em -> holder[0] = em.createQuery(
                "select u from User u where u.username = :n", User.class)
            .setParameter("n", username).getSingleResult().getId());
        return holder[0];
    }

    private static long roleIdOf(String name) {
        final long[] holder = new long[1];
        inTransaction(em -> holder[0] = em.createQuery(
                "select r.id from Role r where r.name = :n", Long.class)
            .setParameter("n", name).getSingleResult());
        return holder[0];
    }
}
