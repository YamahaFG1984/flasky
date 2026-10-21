package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.util.Markdown;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    void Markdown渲染并清洗掉危险内容() {
        String html = Markdown.toSafeHtml("""
            ## 标题

            **粗体** 和 `代码`

            <script>alert('xss')</script>

            <a href="javascript:alert(1)">点我</a>

            <img src=x onerror="alert(1)">
            """);

        assertThat(html).contains("<h2>标题</h2>").contains("<strong>粗体</strong>");
        assertThat(html).contains("<code>代码</code>");
        assertThat(html).doesNotContain("<script").doesNotContain("onerror");
        assertThat(html).doesNotContain("javascript:");
        assertThat(html).doesNotContain("<img");
    }

    @Test
    void 外链自动加上nofollow() {
        assertThat(Markdown.toSafeHtml("[x](https://example.com)"))
            .contains("rel=\"nofollow\"");
    }

    @Test
    void 发文需要WRITE权限() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        // “测试角色”在 TestHttp 里会被造成拥有全部权限，所以这里直接用没有角色的用户
        createUserWithoutRole("noperm@example.com", "noperm");

        JavalinTest.test(app, (server, client) -> {
            login(client, "noperm@example.com");
            var r = postFormWithCsrf(client, "/", "/post", form("body", "我想发文"));
            assertThat(r.code()).isEqualTo(403);
        });
    }

    @Test
    void 发文后首页能看到渲染好的HTML() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");

            var r = postFormWithCsrf(client, "/", "/post",
                form("body", "# 大标题\n\n**加粗**的正文"));
            assertThat(r.code()).isEqualTo(302);

            String index = client.get("/").body().string();
            assertThat(index).contains("<strong>加粗</strong>").contains("文章已发布");
            // 作者主页也应该有
            assertThat(client.get("/user/john").body().string()).contains("<strong>加粗</strong>");
        });
    }

    @Test
    void 不能编辑别人的文章() {
        var app = JavalinkyApp.create(AppConfig.load());
        seedRoles();
        createUser("john@example.com", "john", "普通用户");
        createUser("susan@example.com", "susan", "普通用户");

        JavalinTest.test(app, (server, client) -> {
            login(client, "john@example.com");
            postFormWithCsrf(client, "/", "/post", form("body", "john 的文章"));

            // 从首页里抠出文章 id
            String index = client.get("/").body().string();
            var m = java.util.regex.Pattern.compile("/edit/(\\d+)").matcher(index);
            assertThat(m.find()).isTrue();
            String postId = m.group(1);

            // 本人可以编辑
            assertThat(client.get("/edit/" + postId).code()).isEqualTo(200);

            // 换成 susan 就不行了（她有 WRITE 权限，但这篇不是她的）
            switchUser(client, "susan@example.com");
            assertThat(client.get("/edit/" + postId).code()).isEqualTo(403);
        });
    }

    @Test
    void 分页页码会被规整到合法范围() {
        assertThat(com.example.javalinky.db.Page.clampPage(null)).isEqualTo(1);
        assertThat(com.example.javalinky.db.Page.clampPage(-5)).isEqualTo(1);
        assertThat(com.example.javalinky.db.Page.clampPage(0)).isEqualTo(1);
        assertThat(com.example.javalinky.db.Page.clampPage(7)).isEqualTo(7);
        assertThat(com.example.javalinky.db.Page.clampPage(999_999)).isEqualTo(100_000);
    }

    @Test
    void 页码窗口里有省略号() {
        var page = com.example.javalinky.db.Page.of(java.util.List.of(), 7, 10, 200);
        assertThat(page.totalPages()).isEqualTo(20);
        assertThat(page.pageNumbers()).containsExactly(1, 0, 5, 6, 7, 8, 9, 0, 20);
    }
}
