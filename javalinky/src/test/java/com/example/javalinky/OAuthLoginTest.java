package com.example.javalinky;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.service.OAuthClient;
import io.javalin.Javalin;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 第三方登录看着「必须连外网才能测」，其实不是：
 * <b>服务商就是几个 HTTP 端点</b>，在测试里自己起一个假的就行。
 * 这样连「对方返回错误」「对方不给邮箱」这些分支都能覆盖到。
 */
class OAuthLoginTest {

    private Javalin fakeProvider;
    private int providerPort;

    /** 假服务商：记下收到的参数，好断言我们发出去的请求是对的 */
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private volatile String tokenResponse  = "{\"access_token\":\"fake-token\"}";
    private volatile int tokenStatus       = 200;
    private volatile String userResponse   =
        "{\"id\":\"gh-123\",\"email\":\"octocat@example.com\",\"login\":\"octocat\"}";
    private volatile int userStatus        = 200;

    private void startFakeProvider() {
        fakeProvider = Javalin.create(config -> {
            config.routes.post("/token", ctx -> {
                received.put("code", String.valueOf(ctx.formParam("code")));
                received.put("code_verifier", String.valueOf(ctx.formParam("code_verifier")));
                received.put("client_id", String.valueOf(ctx.formParam("client_id")));
                received.put("client_secret", String.valueOf(ctx.formParam("client_secret")));
                received.put("redirect_uri", String.valueOf(ctx.formParam("redirect_uri")));
                ctx.status(tokenStatus).contentType("application/json").result(tokenResponse);
            });
            config.routes.get("/userinfo", ctx -> {
                received.put("authorization", String.valueOf(ctx.header("Authorization")));
                ctx.status(userStatus).contentType("application/json").result(userResponse);
            });
        }).start(0);
        providerPort = fakeProvider.port();
    }

    @AfterEach
    void stopFakeProvider() {
        if (fakeProvider != null) fakeProvider.stop();
    }

    /** 把应用的 OAuth 配置指向假服务商 */
    private AppConfig configPointingAtFakeProvider() {
        startFakeProvider();
        var base = TestFixtures.config();
        String url = "http://localhost:" + providerPort;
        return new AppConfig(
            base.profile(), base.port(), base.siteBaseUrl(),
            base.jdbcUrl(), base.dbUser(), base.dbPassword(),
            base.secretKey(), base.httpsOnly(), base.behindProxy(),
            base.mail(), base.adminEmail(),
            base.postsPerPage(), base.followersPerPage(), base.commentsPerPage(),
            base.slowQueryMillis(),
            base.loginAttempts(), base.loginWindowSeconds(),
            base.avatarDir(), base.sseHeartbeatMillis(),
            "test-client-id", "test-client-secret",
            url + "/authorize", url + "/token", url + "/userinfo",
            base.devLogging(), base.routeOverview(), base.templateHotReload());
    }

    // ---------- 没配置时就等于没这个功能 ----------

    @Test
    void 没配clientId时第三方登录是404() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            assertThat(client.get("/auth/oauth/github").code())
                .as("没配置就不该暴露这个入口")
                .isEqualTo(404);
        });
    }

    @Test
    void 不认识的服务商是404() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            assertThat(client.get("/auth/oauth/evilcorp").code()).isEqualTo(404);
        });
    }

    // ---------- 第一步：跳转 ----------

    @Test
    void 跳转地址带着state和PKCE() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            var res = client.get("/auth/oauth/github");

            assertThat(res.code()).isEqualTo(302);
            String location = res.headers().get("Location").get(0);

            assertThat(location)
                .startsWith("http://localhost:" + providerPort + "/authorize")
                .contains("client_id=test-client-id")
                .contains("response_type=code")
                .contains("code_challenge_method=S256")
                .contains("state=")
                .contains("code_challenge=");

            assertThat(location)
                .as("绝不能把 client_secret 放进跳转地址 —— 那是给浏览器看的")
                .doesNotContain("test-client-secret");
        });
    }

    // ---------- 第二步：回调 ----------

    @Test
    void state对不上就拒绝() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            client.get("/auth/oauth/github");    // 先走一遍，让会话里有 state

            var res = client.get("/auth/oauth/github/callback?code=abc&state=伪造的");

            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
            assertThat(received)
                .as("state 不对就不该去换令牌")
                .doesNotContainKey("code");
        });
    }

    @Test
    void 没有state的回调也拒绝() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            // 直接访问回调，没走第一步
            var res = client.get("/auth/oauth/github/callback?code=abc&state=whatever");

            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
            assertThat(received).doesNotContainKey("code");
        });
    }

    @Test
    void 完整流程会创建一个新账号() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            String state = startAndGetState(client);

            var res = client.get("/auth/oauth/github/callback?code=the-code&state=" + state);
            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).doesNotContain("/auth/login");

            // 我们发给服务商的参数对不对
            assertThat(received.get("code")).isEqualTo("the-code");
            assertThat(received.get("client_secret")).isEqualTo("test-client-secret");
            assertThat(received.get("code_verifier")).as("PKCE 的 verifier 要带上").isNotBlank();
            assertThat(received.get("authorization")).isEqualTo("Bearer fake-token");

            // 账号真的建出来了，而且是已确认状态
            var created = TestFixtures.withTransactionReturning(em ->
                new UserRepository(em).findByEmail("octocat@example.com").orElse(null));
            assertThat(created).isNotNull();
            assertThat(created.getUsername()).isEqualTo("octocat");
            assertThat(created.isConfirmed())
                .as("邮箱由服务商验证过，不用再发确认信")
                .isTrue();

            assertThat(client.get("/edit-profile").code()).as("应该已经登录了").isEqualTo(200);
        });
    }

    @Test
    void 第二次用同一个第三方账号登录不会重复建号() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();

            String state1 = startAndGetState(client);
            client.get("/auth/oauth/github/callback?code=c1&state=" + state1);
            logout(client);

            String state2 = startAndGetState(client);
            client.get("/auth/oauth/github/callback?code=c2&state=" + state2);

            long count = TestFixtures.withTransactionReturning(em ->
                em.createQuery("select count(u) from User u", Long.class).getSingleResult());
            assertThat(count).as("同一个 GitHub 账号只该对应一个本地账号").isEqualTo(1L);
        });
    }

    @Test
    void 服务商不给邮箱时不创建账号() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            userResponse = "{\"id\":\"gh-999\",\"email\":null,\"login\":\"ghost\"}";

            String state = startAndGetState(client);
            var res = client.get("/auth/oauth/github/callback?code=x&state=" + state);

            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
            long count = TestFixtures.withTransactionReturning(em ->
                em.createQuery("select count(u) from User u", Long.class).getSingleResult());
            assertThat(count).isZero();
        });
    }

    @Test
    void 不会关联到未验证邮箱的已有账号() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            // 有人抢先用这个邮箱注册了，但没验证
            TestFixtures.withTransaction(em -> {
                var squatter = TestFixtures.user(em, "squatter");
                squatter.setEmail("octocat@example.com");
                squatter.setConfirmed(false);
            });

            String state = startAndGetState(client);
            var res = client.get("/auth/oauth/github/callback?code=x&state=" + state);

            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
            assertThat(client.get("/edit-profile", req -> req.header("Accept", "text/html")).code())
                .as("绝不能让第三方登录接管一个未验证邮箱的账号")
                .isIn(302, 401);
        });
    }

    @Test
    void 服务商换令牌失败时不创建账号() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            tokenStatus = 401;
            tokenResponse = "{\"error\":\"bad_verification_code\"}";

            String state = startAndGetState(client);
            var res = client.get("/auth/oauth/github/callback?code=x&state=" + state);

            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
            long count = TestFixtures.withTransactionReturning(em ->
                em.createQuery("select count(u) from User u", Long.class).getSingleResult());
            assertThat(count).isZero();
        });
    }

    @Test
    void 用户点取消时不报错() {
        JavalinTest.test(JavalinkyApp.create(configPointingAtFakeProvider()), (server, client) -> {
            seedRoles();
            String state = startAndGetState(client);

            // 服务商在用户拒绝时会带 error 回来，没有 code
            var res = client.get("/auth/oauth/github/callback?error=access_denied&state=" + state);

            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
        });
    }

    // ---------- 工具 ----------

    /** 走第一步，从跳转地址里把 state 抠出来（会话里的那个值） */
    private static String startAndGetState(io.javalin.testtools.HttpClient client) {
        var res = client.get("/auth/oauth/github");
        String location = res.headers().get("Location").get(0);
        var m = java.util.regex.Pattern.compile("[?&]state=([^&]+)").matcher(location);
        assertThat(m.find()).as("跳转地址里应该有 state").isTrue();
        return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }
}
