package com.example.javalinky;

import com.example.javalinky.model.Post;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 把“首页的查询条数不随文章数增长”这件事钉死。
 * 查询条数不用额外接口读 —— Timing 过滤器已经把它写进了 Server-Timing 响应头。
 */
class NPlusOneTest {

    private static final Pattern QUERIES = Pattern.compile("queries;desc=\"(\\d+)\"");

    @Test
    void 首页列20篇文章时查询条数必须是常数() {
        var app = JavalinkyApp.create(TestFixtures.config());

        JavalinTest.test(app, (server, client) -> {
            // 先用 1 篇文章量一个基准
            TestFixtures.withTransaction(em -> {
                TestFixtures.seedRoles(em);
                em.persist(new Post("文章 0", TestFixtures.user(em, "user0")));
            });
            int withOnePost = queryCount(client.get("/"));

            // 再加到 20 篇，作者各不相同
            TestFixtures.withTransaction(em -> {
                for (int i = 1; i < 20; i++) {
                    var user = TestFixtures.user(em, "user" + i);
                    em.persist(new Post("文章 " + i, user));
                }
            });
            int withTwentyPosts = queryCount(client.get("/"));

            // 关键断言：条数不随文章数增长（join fetch 作者 + 一条聚合查评论数）
            assertThat(withTwentyPosts)
                .as("1 篇时 %d 条 SQL，20 篇时 %d 条 —— 多出来的就是 N+1",
                    withOnePost, withTwentyPosts)
                .isEqualTo(withOnePost);

            // 顺手给个绝对上限：count + 主查询 + 评论数聚合
            assertThat(withTwentyPosts).isLessThanOrEqualTo(6);
        });
    }

    private static int queryCount(Response response) {
        var values = response.headers().get("Server-Timing");
        String timing = values.isEmpty() ? "" : values.get(0);
        var m = QUERIES.matcher(timing);
        assertThat(m.find()).as("Server-Timing 头里应该有 queries；实际是 %s", timing).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
