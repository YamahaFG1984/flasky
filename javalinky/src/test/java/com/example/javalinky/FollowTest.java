package com.example.javalinky;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

class FollowTest {

    @Test
    void 关注与取消关注() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUser("susan@example.com", "susan", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");

            // 关注
            var r = postFormWithCsrf(client, "/user/susan", "/follow/susan", form());
            assertThat(r.code()).isEqualTo(302);

            String susanPage = client.get("/user/susan").body().string();
            assertThat(susanPage).contains("已关注 susan").contains("取消关注");

            // susan 的粉丝列表里有 john
            assertThat(client.get("/user/susan/followers").body().string()).contains("john");
            // john 关注的人里有 susan
            assertThat(client.get("/user/john/following").body().string()).contains("susan");

            // 幂等：再关注一次不会报错，也不会多一条
            assertThat(postFormWithCsrf(client, "/user/susan", "/follow/susan", form()).code())
                .isEqualTo(302);

            // 取关
            assertThat(postFormWithCsrf(client, "/user/susan", "/unfollow/susan", form()).code())
                .isEqualTo(302);
            assertThat(client.get("/user/susan").body().string()).contains("已取消关注 susan");
        });
    }

    @Test
    void 不能关注自己() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            postFormWithCsrf(client, "/user/john", "/follow/john", form());
            assertThat(client.get("/user/john").body().string()).contains("你不能关注自己");
        });
    }

    @Test
    void 自关注不计入对外显示的粉丝数() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();

        JavalinTest.test(app, (server, client) -> {
            // 走真正的注册流程：注册时会自动自关注
            var r = postFormWithCsrf(client, "/auth/register", "/auth/register", form(
                "email", "john@example.com", "username", "john",
                "password", "cat12345", "password2", "cat12345"));
            assertThat(r.code()).isEqualTo(302);

            String page = client.get("/user/john").body().string();
            // 粉丝 0、关注 0（自关注那条被减掉了）
            assertThat(page).contains("粉丝 <span class=\"badge text-bg-secondary\">0</span>");
            assertThat(page).contains("关注 <span class=\"badge text-bg-secondary\">0</span>");
        });
    }

    @Test
    void 只看关注的时间线一条SQL就能查出来() {
        var app = JavalinkyApp.create(TestFixtures.config());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUser("susan@example.com", "susan", "普通用户");
        createUser("stranger@example.com", "stranger", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            // susan 和 stranger 各发一篇
            login(client, "susan@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "susan 的文章"));
            switchUser(client, "stranger@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "陌生人的文章"));

            // john 关注 susan，然后切到“我关注的”
            switchUser(client, "john@example.com");
            postFormWithCsrf(client, "/user/susan", "/follow/susan", form());

            // 默认“全部”：两篇都在
            String all = client.get("/").body().string();
            assertThat(all).contains("susan 的文章").contains("陌生人的文章");

            assertThat(postFormWithCsrf(client, "/", "/show-followed", form()).code())
                .isEqualTo(302);

            String followed = client.get("/").body().string();
            assertThat(followed).contains("susan 的文章");
            assertThat(followed).doesNotContain("陌生人的文章");

            // 切回全部
            postFormWithCsrf(client, "/", "/show-all", form());
            assertThat(client.get("/").body().string()).contains("陌生人的文章");
        });
    }
}
