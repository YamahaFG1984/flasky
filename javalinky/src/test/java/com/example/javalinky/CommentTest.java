package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    void 评论发布后出现在文章页() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "一篇文章"));
            long postId = firstPostId(client.get("/").body().string());

            String url = "/post/" + postId;
            var r = postFormWithCsrf(client, url, url + "/comments",
                form("body", "**第一条**评论"));
            assertThat(r.code()).isEqualTo(302);
            // 跳到最后一页并带上 #comments 锚点
            assertThat(r.headers().get("Location").get(0)).contains("page=-1").contains("#comments");

            String page = client.get(url).body().string();
            assertThat(page).contains("<strong>第一条</strong>").contains("评论（1）");
        });
    }

    @Test
    void 没有COMMENT权限的人看不到评论框() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUserWithoutRole("noperm@example.com", "noperm");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "一篇文章"));
            long postId = firstPostId(client.get("/").body().string());

            switchUser(client, "noperm@example.com");
            String page = client.get("/post/" + postId).body().string();
            assertThat(page).doesNotContain("发表评论");

            // 直接 POST 也不行
            assertThat(postForm(client, "/post/" + postId + "/comments",
                form("body", "偷偷评论")).code()).isIn(403, 302);
        });
    }

    @Test
    void 协管员可以隐藏和恢复评论而普通读者看不到被隐藏的() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUser("mod@example.com", "mod", "协管员");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "一篇文章"));
            long postId = firstPostId(client.get("/").body().string());
            String url = "/post/" + postId;
            postFormWithCsrf(client, url, url + "/comments", form("body", "不太礼貌的评论"));

            // 普通用户看不到协管入口
            assertThat(client.get("/").body().string()).doesNotContain("协管评论");

            switchUser(client, "mod@example.com");
            String moderate = client.get("/moderate").body().string();
            assertThat(moderate).contains("不太礼貌的评论").contains("隐藏这条");

            var m = Pattern.compile("/moderate/disable/(\\d+)").matcher(moderate);
            assertThat(m.find()).isTrue();
            String commentId = m.group(1);

            assertThat(postFormWithCsrf(client, "/moderate",
                "/moderate/disable/" + commentId, form()).code()).isEqualTo(302);

            // 协管员自己还能看到（带“已隐藏”标记）
            assertThat(client.get(url).body().string())
                .contains("不太礼貌的评论").contains("已隐藏");

            // 换回普通作者：评论没了，计数也归零
            switchUser(client, "john@example.com");
            String asAuthor = client.get(url).body().string();
            assertThat(asAuthor).doesNotContain("不太礼貌的评论");
            assertThat(asAuthor).contains("评论（0）");

            // 恢复
            switchUser(client, "mod@example.com");
            postFormWithCsrf(client, "/moderate", "/moderate/enable/" + commentId, form());
            switchUser(client, "john@example.com");
            assertThat(client.get(url).body().string()).contains("不太礼貌的评论");
        });
    }

    @Test
    void 文章列表的评论数只用一条聚合查询() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            for (int i = 0; i < 3; i++) {
                postFormWithCsrf(client, "/", "/post", form("body", "文章 " + i));
            }
            String index = client.get("/").body().string();
            // 三篇都显示“0 条评论”，说明 Map 的 getOrDefault 生效了
            assertThat(index.split("条评论", -1).length - 1).isEqualTo(3);
        });
    }

    private static long firstPostId(String html) {
        var m = Pattern.compile("/post/(\\d+)").matcher(html);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }
}
