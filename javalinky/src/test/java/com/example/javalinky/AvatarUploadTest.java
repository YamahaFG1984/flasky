package com.example.javalinky;

import com.example.javalinky.service.AvatarStore;
import com.example.javalinky.util.ImageType;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.example.javalinky.TestHttp.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件上传的测试重点不在「正常路径能不能跑通」，
 * 而在<b>各种不正常的输入会不会被挡住</b> —— 上传是攻击面最大的功能之一。
 */
class AvatarUploadTest {

    // 最小的合法 PNG：8 字节签名 + 一段够长的内容
    private static final byte[] PNG = png();
    private static final byte[] GIF = concat("GIF89a".getBytes(), new byte[64]);

    // ---------- 识别：只看内容，不看扩展名 ----------

    @Test
    void 按魔数识别图片类型() {
        assertThat(ImageType.detect(head(PNG))).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect(head(GIF))).isEqualTo(ImageType.GIF);
        assertThat(ImageType.detect(head(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0})))
            .isEqualTo(ImageType.JPEG);
    }

    @Test
    void 改了扩展名的非图片会被识破() {
        // 一个 JSP webshell，被改名叫 avatar.png
        byte[] shell = "<%@ page import=\"java.io.*\" %><% Runtime.getRuntime().exec(...); %>"
            .getBytes();
        assertThat(ImageType.detect(head(shell)))
            .as("内容不是图片就必须认不出来")
            .isNull();
    }

    @Test
    void RIFF容器不是webp就不认() {
        // RIFF 开头的还有 wav、avi。只匹配前四字节会误判
        byte[] wav = concat("RIFF".getBytes(), concat(new byte[4], "WAVE".getBytes()));
        assertThat(ImageType.detect(head(wav))).isNull();
    }

    // ---------- 存储 ----------

    @Test
    void 存下来的文件名由服务端生成() {
        var store = newStore();
        var result = store.store(new ByteArrayInputStream(PNG), PNG.length);

        assertThat(result).isInstanceOf(AvatarStore.Result.Stored.class);
        String name = ((AvatarStore.Result.Stored) result).filename();

        assertThat(name)
            .as("不能带上客户端给的任何东西")
            .matches("[0-9a-f]{32}\\.png");
    }

    @Test
    void 太大的文件会被拒() {
        var store = newStore();
        byte[] big = new byte[(int) AvatarStore.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        var result = store.store(new ByteArrayInputStream(big), big.length);
        assertThat(result).isInstanceOf(AvatarStore.Result.Rejected.class);
        assertThat(((AvatarStore.Result.Rejected) result).reason())
            .isEqualTo(AvatarStore.Rejection.TOO_LARGE);
    }

    @Test
    void 谎报大小也拦得住() {
        var store = newStore();
        byte[] big = new byte[(int) AvatarStore.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        // 客户端说自己只有 100 字节，实际发了 2MB+
        var result = store.store(new ByteArrayInputStream(big), 100);

        assertThat(result)
            .as("declaredSize 来自客户端，必须按真实落盘字节数再判一次")
            .isInstanceOf(AvatarStore.Result.Rejected.class);
    }

    @Test
    void 不是图片的内容会被拒() {
        var store = newStore();
        byte[] text = "这其实是一个文本文件".getBytes();
        var result = store.store(new ByteArrayInputStream(text), text.length);

        assertThat(((AvatarStore.Result.Rejected) result).reason())
            .isEqualTo(AvatarStore.Rejection.NOT_AN_IMAGE);
    }

    @Test
    void 目录穿越的文件名取不到东西() throws Exception {
        Path root = Path.of("target/test-avatars/" + UUID.randomUUID());
        Files.createDirectories(root);
        // 在目录<b>外面</b>放一个文件
        Path secret = root.getParent().resolve("secret.txt");
        Files.writeString(secret, "不该被读到");

        var store = new AvatarStore(root);

        assertThat(store.find("../secret.txt")).as("../ 必须被挡住").isEmpty();
        assertThat(store.find("/etc/passwd")).as("绝对路径也要挡住").isEmpty();
        assertThat(store.find("..%2Fsecret.txt")).isEmpty();
        assertThat(store.find(null)).isEmpty();
        assertThat(store.find("")).isEmpty();

        Files.deleteIfExists(secret);
    }

    // ---------- 走 HTTP 的完整流程 ----------

    @Test
    void 上传头像之后资料页用的是上传的那张() {
        var cfg = TestFixtures.config();
        JavalinTest.test(JavalinkyApp.create(cfg), (server, client) -> {
            seedRoles();
            createUser("john@example.com", "john", "普通用户");
            login(client, "john@example.com");

            var res = postMultipartWithCsrf(client, "/edit-profile", "/edit-profile",
                Map.of("name", "John", "location", "", "aboutMe", ""),
                new FilePart("avatar", "me.png", "image/png", PNG));

            assertThat(res.code()).as("上传成功应该重定向").isEqualTo(302);

            String profile = client.get("/user/john").body().string();
            assertThat(profile)
                .as("资料页应该指向 /avatars/ 而不是 gravatar")
                .contains("/avatars/")
                .doesNotContain("gravatar.com");
        });
    }

    @Test
    void 上传非图片会带着错误信息回到表单() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedRoles();
            createUser("john@example.com", "john", "普通用户");
            login(client, "john@example.com");

            var res = postMultipartWithCsrf(client, "/edit-profile", "/edit-profile",
                Map.of("name", "John", "location", "", "aboutMe", ""),
                new FilePart("avatar", "shell.png", "image/png", "not an image".getBytes()));

            assertThat(res.code()).as("校验失败是重新渲染表单，不是重定向").isEqualTo(200);
            String html = res.body().string();
            assertThat(html).contains("只支持 PNG");
            assertThat(html).as("已填的文字要带回来").contains("John");
        });
    }

    @Test
    void 不选文件时不会动原来的头像() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            seedRoles();
            createUser("john@example.com", "john", "普通用户");
            login(client, "john@example.com");

            postMultipartWithCsrf(client, "/edit-profile", "/edit-profile",
                Map.of("name", "John", "location", "", "aboutMe", ""),
                new FilePart("avatar", "me.png", "image/png", PNG));
            String before = client.get("/user/john").body().string();

            // 这次只改文字，不带文件
            postFormWithCsrf(client, "/edit-profile", "/edit-profile",
                form("name", "John Doe", "location", "北京", "aboutMe", ""));
            String after = client.get("/user/john").body().string();

            String url = between(before, "/avatars/", "\"");
            assertThat(after).as("头像不该被清掉").contains("/avatars/" + url);
            assertThat(after).contains("北京");
        });
    }

    @Test
    void 取一个不存在的头像返回404() {
        JavalinTest.test(JavalinkyApp.create(TestFixtures.config()), (server, client) -> {
            assertThat(client.get("/avatars/doesnotexist.png").code()).isEqualTo(404);
        });
    }

    // ---------- 工具 ----------

    private static AvatarStore newStore() {
        return new AvatarStore(Path.of("target/test-avatars/" + UUID.randomUUID()));
    }

    private static byte[] head(byte[] data) {
        return java.util.Arrays.copyOf(data, Math.min(ImageType.MAGIC_BYTES, data.length));
    }

    private static byte[] png() {
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        return concat(sig, new byte[64]);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String between(String s, String start, String end) {
        int i = s.indexOf(start) + start.length();
        return s.substring(i, s.indexOf(end, i));
    }
}
