package com.example.javalinky.e2e;

import com.example.javalinky.JavalinkyApp;
import com.example.javalinky.TestFixtures;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * 端到端测试：真浏览器点真页面。默认不跑（surefire 排除了 **&#47;e2e/**），
 * 先装一次浏览器，再用 mvn -Pe2e test 跑：
 *
 *   mvn exec:java -Dexec.mainClass=com.microsoft.playwright.CLI \
 *                 -Dexec.classpathScope=test -Dexec.args="install chromium"
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EndToEndTest {

    private Javalin app;
    private int port;
    private Playwright playwright;
    private Browser browser;

    @BeforeAll
    void startEverything() {
        // 端到端测试比较贵，所以整个类共用一个应用和一个浏览器
        app = JavalinkyApp.create(TestFixtures.config());
        app.start(0);                       // 0 = 随机空闲端口
        port = app.port();

        TestFixtures.withTransaction(em -> {
            TestFixtures.seedRoles(em);
            TestFixtures.user(em, "john");
        });

        playwright = Playwright.create();
        browser = playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void stopEverything() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
        if (app != null) app.stop();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void 用户能登录并发一篇文章() {
        try (BrowserContext context = browser.newContext()) {   // 每个测试一个干净的浏览器上下文
            Page page = context.newPage();

            // ① 打开首页
            page.navigate(url("/"));
            assertThat(page.locator("h1")).containsText("欢迎来到 Javalinky");

            // ② 点“登录”
            page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("登录")).first().click();

            // ③ 填表单。Playwright 会自动等元素出现，不需要 sleep
            page.getByLabel("邮箱").fill("john@example.com");
            page.getByLabel("密码").fill("cat12345");
            page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("登录")).click();

            // ④ 确认登录成功
            assertThat(page.locator("h1")).containsText("john");

            // ⑤ 发一篇文章。EasyMDE 会把 textarea 换成 CodeMirror，
            //    所以要往编辑器里敲字，而不是 fill 那个已经被隐藏的 textarea。
            //    （CDN 拿不到 EasyMDE 时会退化成普通 textarea，这里两种都兼容）
            String text = "端到端测试发的文章";
            if (page.locator(".CodeMirror").count() > 0) {
                page.locator(".CodeMirror").click();
                page.keyboard().type(text);
            } else {
                page.locator("textarea[name=body]").fill(text);
            }
            page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("发布")).click();

            // ⑥ 确认文章出现在列表里
            assertThat(page.locator(".post-list")).containsText(text);
        }
    }

    @Test
    void 没登录时看不到发文表单() {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.navigate(url("/"));
            org.assertj.core.api.Assertions
                .assertThat(page.locator("textarea[name=body]").count()).isZero();
        }
    }
}
