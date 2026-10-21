package com.example.javalinky;

import com.example.javalinky.model.Post;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 的测试比 SSE 简单些：JDK 自带客户端，而且
 * <b>协议层面有明确的关闭帧</b>，不需要靠心跳猜对面还在不在。
 */
class PostPresenceTest {

    @Test
    void 在场人数会广播给所有连接() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            long postId = seedPost();
            String url = "ws://localhost:" + server.port() + "/ws/post/" + postId;

            var a = Ws.connect(url);
            assertThat(a.await(1, 5)).as("第一个连接应该收到一条 viewers").isTrue();
            assertThat(a.last()).contains("\"count\":1");

            var b = Ws.connect(url);
            // 新人进来，所有人都该收到更新
            assertThat(b.await(1, 5)).isTrue();
            assertThat(a.await(2, 5)).as("A 也要收到人数变化").isTrue();
            assertThat(a.last()).contains("\"count\":2");

            b.close();
            assertThat(a.await(3, 5)).as("有人离开也要广播").isTrue();
            assertThat(a.last()).contains("\"count\":1");

            a.close();
        });
    }

    @Test
    void 正在输入会转发给别人但不回给自己() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            long postId = seedPost();
            String url = "ws://localhost:" + server.port() + "/ws/post/" + postId;

            String bobCookies = loginAndGetCookies(server.port(), "bob@example.com");

            var a = Ws.connect(url);
            var b = Ws.connect(url, bobCookies);     // B 是登录状态
            a.await(2, 5);           // 等人数广播稳定
            b.await(1, 5);

            int aBefore = a.count();
            b.send("typing");

            boolean got = a.await(aBefore + 1, 5);
            assertThat(got)
                .as("A 应该收到 typing；A 实际收到的是 %s（aBefore=%d）", a.all(), aBefore)
                .isTrue();
            assertThat(a.last()).contains("\"type\":\"typing\"");

            // B 自己不该收到
            Thread.sleep(500);
            assertThat(b.all().stream().noneMatch(m -> m.contains("\"typing\"")))
                .as("发起者不该收到自己的 typing")
                .isTrue();

            a.close();
            b.close();
        });
    }

    @Test
    void 未登录时不广播用户名() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            long postId = seedPost();
            String url = "ws://localhost:" + server.port() + "/ws/post/" + postId;

            var a = Ws.connect(url);
            var b = Ws.connect(url);
            a.await(2, 5);

            int aBefore = a.count();
            b.send("typing");          // 匿名连接

            Thread.sleep(800);
            assertThat(a.count())
                .as("拿不到用户名就不该广播 —— 用户名只能来自服务端会话")
                .isEqualTo(aBefore);

            a.close();
            b.close();
        });
    }

    @Test
    void 不认识的消息被忽略而不是让连接崩掉() throws Exception {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, ignored) -> {
            long postId = seedPost();
            String url = "ws://localhost:" + server.port() + "/ws/post/" + postId;

            var a = Ws.connect(url);
            a.await(1, 5);

            a.send("{\"随便\":\"什么\"}");
            a.send("");
            Thread.sleep(300);

            // 连接还活着：再来一个人，人数广播照常
            var b = Ws.connect(url);
            assertThat(a.await(2, 5)).as("连接不该因为垃圾消息而断掉").isTrue();

            a.close();
            b.close();
        });
    }

    // ---------- 夹具 ----------

    private static long seedPost() {
        var id = new AtomicLong();
        TestFixtures.withTransaction(em -> {
            TestFixtures.seedRoles(em);
            var bob = TestFixtures.user(em, "bob");
            bob.setEmail("bob@example.com");
            var post = new Post("一篇文章", TestFixtures.user(em, "alice"));
            em.persist(post);
            em.flush();
            id.set(post.getId());
        });
        return id.get();
    }

    /** 用原生客户端登录一次，返回可以直接塞进握手的 Cookie 头 */
    private static String loginAndGetCookies(int port, String email) throws Exception {
        var cookieManager = new java.net.CookieManager();
        var http = HttpClient.newBuilder()
            .cookieHandler(cookieManager)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        String base = "http://localhost:" + port;

        var page = http.send(
            java.net.http.HttpRequest.newBuilder(URI.create(base + "/auth/login")).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        var m = java.util.regex.Pattern
            .compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(page.body());
        assertThat(m.find()).as("登录页应该有 _csrf").isTrue();

        String body = "_csrf=" + java.net.URLEncoder.encode(m.group(1), java.nio.charset.StandardCharsets.UTF_8)
            + "&email=" + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
            + "&password=cat12345";
        var res = http.send(
            java.net.http.HttpRequest.newBuilder(URI.create(base + "/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).as("登录应该 302").isEqualTo(302);

        var sb = new StringBuilder();
        for (var c : cookieManager.getCookieStore().getCookies()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(c.getName()).append('=').append(c.getValue());
        }
        return sb.toString();
    }

    // ---------- 极简 WebSocket 客户端 ----------

    private static final class Ws implements WebSocket.Listener, AutoCloseable {
        private final List<String> messages = new ArrayList<>();
        private final List<CountDownLatch> waiters = new ArrayList<>();
        private WebSocket socket;

        static Ws connect(String url) throws Exception {
            return connect(url, null);
        }

        /** cookies 不为 null 时带上会话 —— WebSocket 握手就是一次普通的 HTTP 请求 */
        static Ws connect(String url, String cookies) throws Exception {
            var listener = new Ws();
            var builder = HttpClient.newHttpClient().newWebSocketBuilder();
            if (cookies != null) builder.header("Cookie", cookies);
            listener.socket = builder
                .buildAsync(URI.create(url), listener)
                .get(10, TimeUnit.SECONDS);
            return listener;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            synchronized (this) {
                messages.add(data.toString());
                waiters.forEach(CountDownLatch::countDown);
            }
            ws.request(1);
            return null;
        }

        /** 等到收够 n 条消息 */
        boolean await(int n, int seconds) throws InterruptedException {
            long deadline = System.nanoTime() + seconds * 1_000_000_000L;
            while (System.nanoTime() < deadline) {
                CountDownLatch latch;
                synchronized (this) {
                    if (messages.size() >= n) return true;
                    latch = new CountDownLatch(1);
                    waiters.add(latch);
                }
                latch.await(200, TimeUnit.MILLISECONDS);
            }
            synchronized (this) {
                return messages.size() >= n;
            }
        }

        synchronized int count()           { return messages.size(); }
        synchronized String last()         { return messages.isEmpty() ? "" : messages.get(messages.size() - 1); }
        synchronized List<String> all()    { return List.copyOf(messages); }

        void send(String text) {
            socket.sendText(text, true).join();
        }

        @Override public void close() {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        }
    }
}
