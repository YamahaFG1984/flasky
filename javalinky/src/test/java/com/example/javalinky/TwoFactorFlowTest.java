package com.example.javalinky;

import com.example.javalinky.db.UserRepository;
import com.example.javalinky.service.TwoFactorService;
import com.example.javalinky.util.Totp;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 两步验证最容易写错的不是算法（那个有 RFC 向量兜底），
 * 而是<b>状态机</b>：「密码对了但第二步还没过」这个中间态，
 * 绝不能等于「已登录」。
 */
class TwoFactorFlowTest {

    @Test
    void 开启两步验证之后登录会先要验证码() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            String secret = seedUserWith2fa("john@example.com", "john");

            var res = postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));

            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0))
                .as("密码对了也不能直接进去")
                .contains("/auth/2fa");

            // 关键断言：这时候还不是登录态
            var home = client.get("/edit-profile", req -> req.header("Accept", "text/html"));
            assertThat(home.code())
                .as("待验证状态绝不能等于已登录")
                .isIn(302, 401);
        });
    }

    @Test
    void 输入正确的验证码才算登录成功() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            String secret = seedUserWith2fa("john@example.com", "john");

            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));

            String code = Totp.currentCode(secret, Instant.now().getEpochSecond());
            var res = postFormWithCsrf(client, "/auth/2fa", "/auth/2fa", form("code", code));

            assertThat(res.code()).isEqualTo(302);
            assertThat(client.get("/edit-profile").code())
                .as("现在应该真的登录了")
                .isEqualTo(200);
        });
    }

    @Test
    void 验证码不对就进不去() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedUserWith2fa("john@example.com", "john");

            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));

            var res = postFormWithCsrf(client, "/auth/2fa", "/auth/2fa", form("code", "000000"));
            assertThat(res.code()).as("重新渲染表单，不是重定向").isEqualTo(200);
            assertThat(res.body().string()).contains("验证码不对");

            assertThat(client.get("/edit-profile", req -> req.header("Accept", "text/html")).code())
                .isIn(302, 401);
        });
    }

    @Test
    void 没走过密码那一步不能直接访问验证页() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedUserWith2fa("john@example.com", "john");

            var res = client.get("/auth/2fa", req -> req.header("Accept", "text/html"));
            assertThat(res.code()).as("应该被打回登录页").isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).contains("/auth/login");
        });
    }

    @Test
    void 同一个验证码不能用第二次() {
        var slot = new AtomicReference<String>();
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            String secret = seedUserWith2fa("john@example.com", "john");
            String code = Totp.currentCode(secret, Instant.now().getEpochSecond());
            slot.set(code);

            // 第一次：成功
            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));
            assertThat(postFormWithCsrf(client, "/auth/2fa", "/auth/2fa", form("code", code)).code())
                .isEqualTo(302);

            // 登出，拿同一个码再来一次
            logout(client);
            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));
            var again = postFormWithCsrf(client, "/auth/2fa", "/auth/2fa", form("code", code));

            assertThat(again.code()).isEqualTo(200);
            assertThat(again.body().string())
                .as("30 秒窗口内同一个码能用两次的话，肩窥/剪贴板泄露就能被利用")
                .contains("已经用过了");
        });
    }

    @Test
    void 恢复码可以登录而且只能用一次() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedUserWith2fa("john@example.com", "john");

            List<String> codes = TestFixtures.withTransactionReturning(em -> {
                var user = new UserRepository(em).findByEmail("john@example.com").orElseThrow();
                return new TwoFactorService(em).regenerateRecoveryCodes(user);
            });
            String recovery = codes.get(0);

            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));
            assertThat(postFormWithCsrf(client, "/auth/2fa", "/auth/2fa",
                form("code", recovery)).code()).isEqualTo(302);
            assertThat(client.get("/edit-profile").code()).isEqualTo(200);

            // 同一个恢复码不能再用
            logout(client);
            postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "john@example.com", "password", "cat12345"));
            var again = postFormWithCsrf(client, "/auth/2fa", "/auth/2fa", form("code", recovery));
            assertThat(again.code()).as("用过的恢复码必须失效").isEqualTo(200);
        });
    }

    @Test
    void 恢复码存的是哈希不是明文() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedUserWith2fa("john@example.com", "john");

            List<String> codes = TestFixtures.withTransactionReturning(em -> {
                var user = new UserRepository(em).findByEmail("john@example.com").orElseThrow();
                return new TwoFactorService(em).regenerateRecoveryCodes(user);
            });

            List<String> stored = TestFixtures.withTransactionReturning(em ->
                em.createQuery("select c.codeHash from RecoveryCode c", String.class)
                  .getResultList());

            assertThat(stored).hasSize(10);
            assertThat(stored).noneMatch(codes::contains);
            assertThat(stored).allMatch(h -> h.startsWith("$2"));   // bcrypt 的前缀
        });
    }

    @Test
    void 没开两步验证的用户登录流程不变() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedRoles();
            createUser("plain@example.com", "plain", "普通用户");

            var res = postFormWithCsrf(client, "/auth/login", "/auth/login",
                form("email", "plain@example.com", "password", "cat12345"));

            assertThat(res.code()).isEqualTo(302);
            assertThat(res.headers().get("Location").get(0)).doesNotContain("2fa");
            assertThat(client.get("/edit-profile").code()).isEqualTo(200);
        });
    }

    // ---------- 夹具 ----------

    /** 造一个已经开好两步验证的用户，返回它的密钥 */
    private static String seedUserWith2fa(String email, String username) {
        seedRoles();
        createUser(email, username, "普通用户");
        String secret = Totp.newSecret();
        TestFixtures.withTransaction(em -> {
            var user = new UserRepository(em).findByEmail(email).orElseThrow();
            user.setTotpSecret(secret);
            user.setTotpEnabled(true);
        });
        return secret;
    }
}
