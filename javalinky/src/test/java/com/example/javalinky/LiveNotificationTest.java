package com.example.javalinky;

import com.example.javalinky.db.UserRepository;
import com.example.javalinky.model.Post;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 的测试比普通请求麻烦两点：
 *   1. 连接<b>不会结束</b>，所以不能等响应读完 —— 要另起线程按行读
 *   2. 需要自己管会话，所以这里全程用 java.net.http 而不是 testtools 的客户端
 */
class LiveNotificationTest {

    private static final Pattern CSRF =
        Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Test
    void 未登录连不上通知流() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            var res = client.get("/live", req -> req.header("Accept", "text/html"));
            assertThat(res.code())
                .as("SSE 也是普通的 HTTP GET，一样受路由上的角色约束")
                .isIn(302, 401);
        });
    }

    @Test
    void 有人评论我的文章时作者会收到推送() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            String base = "http://localhost:" + server.port();
            long postId = seedTwoUsersAndAPost();

            var alice = new Session(base);
            alice.login("alice@example.com");

            try (var stream = alice.openSse("/live")) {
                assertThat(stream.await("hello", 10))
                    .as("连上之后应该先收到 hello").isTrue();

                // bob 用<b>另一个</b>会话评论
                var bob = new Session(base);
                bob.login("bob@example.com");
                int code = bob.postForm("/post/" + postId + "/comments",
                                        "/post/" + postId,
                                        Map.of("body", "写得真好"));
                assertThat(code).as("评论应该成功").isEqualTo(302);

                assertThat(stream.await("comment", 10))
                    .as("alice 应该收到一条 comment 事件").isTrue();

                assertThat(stream.lastDataFor("comment"))
                    .contains("bob")
                    .contains("写得真好");
            }
        });
    }

    @Test
    void 自己评论自己的文章不会给自己发通知() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            String base = "http://localhost:" + server.port();
            long postId = seedTwoUsersAndAPost();

            var alice = new Session(base);
            alice.login("alice@example.com");

            try (var stream = alice.openSse("/live")) {
                assertThat(stream.await("hello", 10)).isTrue();

                alice.postForm("/post/" + postId + "/comments",
                               "/post/" + postId, Map.of("body", "自言自语"));

                assertThat(stream.await("comment", 2))
                    .as("不该给自己发通知").isFalse();
            }
        });
    }

    /**
     * 断开的连接必须被摘掉，否则 LiveHub 就是个内存泄漏。
     *
     * 这个测试的写法是实测出来的，过程值得记一下：
     *   - 「关掉连接就立刻断言数量变了」不成立 —— TCP 对端关闭不会被服务端立刻感知
     *   - 「推一次消息就会发现」也不成立 —— 往半关闭的 socket 写<b>是成功的</b>，
     *     数据进了内核缓冲区。实测推 4 次，死连接依然挂在那里
     *   - 真正有效的是<b>心跳</b>：定期写一行注释，写不动的才暴露出来
     * 所以这里等的是一个心跳周期（测试档位配成 200ms）。
     */
    @Test
    void 断开的连接会被心跳清理掉() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            String base = "http://localhost:" + server.port();
            seedTwoUsersAndAPost();

            var alice = new Session(base);
            alice.login("alice@example.com");

            var first = alice.openSse("/live");
            assertThat(first.await("hello", 10)).isTrue();
            assertThat(first.lastDataFor("hello")).contains("\"connections\":1");

            first.close();                  // 客户端走了，但服务端还不知道
            Thread.sleep(1_000);            // 给心跳几个周期去发现它

            try (var again = alice.openSse("/live")) {
                assertThat(again.await("hello", 10)).isTrue();
                assertThat(again.lastDataFor("hello"))
                    .as("死连接必须被心跳清掉，否则 LiveHub 只增不减")
                    .contains("\"connections\":1");
            }
        });
    }

    // ---------- 夹具 ----------

    private static long seedTwoUsersAndAPost() {
        var id = new AtomicLong();
        TestFixtures.withTransaction(em -> {
            TestFixtures.seedRoles(em);
            var alice = TestFixtures.user(em, "alice");
            alice.setEmail("alice@example.com");
            var bob = TestFixtures.user(em, "bob");
            bob.setEmail("bob@example.com");
            em.flush();

            var post = new Post("alice 的文章", alice);
            em.persist(post);
            em.flush();
            id.set(post.getId());
        });
        return id.get();
    }

    // ---------- 自己管会话的 HTTP 客户端 ----------

    private static final class Session {
        private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        private final String base;

        Session(String base) { this.base = base; }

        String get(String path) throws Exception {
            var res = http.send(
                HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.body();
        }

        void login(String email) throws Exception {
            int code = postForm("/auth/login", "/auth/login",
                Map.of("email", email, "password", "cat12345"));
            assertThat(code).as("登录 %s 应该 302", email).isEqualTo(302);
        }

        /** tokenPage：从哪个页面取 _csrf */
        int postForm(String path, String tokenPage, Map<String, String> fields) throws Exception {
            var all = new HashMap<String, String>();
            all.put("_csrf", csrfFrom(get(tokenPage)));
            all.putAll(fields);

            var body = new StringBuilder();
            all.forEach((k, v) -> {
                if (!body.isEmpty()) body.append('&');
                body.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
            });

            var res = http.send(HttpRequest.newBuilder(URI.create(base + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.statusCode();
        }

        SseStream openSse(String path) throws Exception {
            return SseStream.open(http, base + path);
        }

        private static String csrfFrom(String html) {
            var m = CSRF.matcher(html);
            assertThat(m.find()).as("页面里应该有 _csrf 隐藏域").isTrue();
            return m.group(1);
        }
    }

    // ---------- 后台读 SSE 流 ----------

    private static final class SseStream implements AutoCloseable {
        private final List<String> events = new ArrayList<>();
        private final List<String> data = new ArrayList<>();
        private final Map<String, CountDownLatch> waiting = new HashMap<>();
        private volatile boolean closed;
        private volatile java.io.InputStream body;

        static SseStream open(HttpClient http, String url) throws Exception {
            var stream = new SseStream();
            var connected = new CountDownLatch(1);

            Thread.ofPlatform().daemon().start(() -> {
                try {
                    var res = http.send(
                        HttpRequest.newBuilder(URI.create(url))
                            .header("Accept", "text/event-stream")
                            .GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                    stream.body = res.body();
                    connected.countDown();
                    if (res.statusCode() != 200) return;

                    try (var reader = new BufferedReader(
                            new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                        String event = null;
                        String line;
                        while (!stream.closed && (line = reader.readLine()) != null) {
                            if (line.startsWith("event:")) {
                                event = line.substring(6).trim();
                            } else if (line.startsWith("data:")) {
                                stream.record(event == null ? "message" : event,
                                              line.substring(5).trim());
                                event = null;
                            }
                        }
                    }
                } catch (Exception e) {
                    connected.countDown();   // 连不上也要放行，免得主线程干等
                }
            });

            connected.await(10, TimeUnit.SECONDS);
            return stream;
        }

        private synchronized void record(String event, String payload) {
            events.add(event);
            data.add(payload);
            var latch = waiting.get(event);
            if (latch != null) latch.countDown();
        }

        boolean await(String event, int seconds) throws InterruptedException {
            CountDownLatch latch;
            synchronized (this) {
                if (events.contains(event)) return true;
                latch = waiting.computeIfAbsent(event, e -> new CountDownLatch(1));
            }
            return latch.await(seconds, TimeUnit.SECONDS);
        }

        synchronized String lastDataFor(String event) {
            for (int i = events.size() - 1; i >= 0; i--) {
                if (events.get(i).equals(event)) return data.get(i);
            }
            return null;
        }

        @Override public void close() {
            closed = true;
            try {
                if (body != null) body.close();   // 让服务端那边的 onClose 跑起来
            } catch (Exception ignored) {
                // 关流时出错无所谓
            }
        }
    }
}
