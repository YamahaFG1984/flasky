package com.example.javalinky;

import com.example.javalinky.util.Totp;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自己实现密码学相关的东西，<b>必须</b>拿官方测试向量对一遍。
 * 「我这边跑着像是对的」完全不算数 —— 算法差一位，
 * 结果照样是六位数字，看不出任何异常，但用户永远登不进来。
 */
class TotpTest {

    /** RFC 6238 附录 B 规定的种子：ASCII 的 "12345678901234567890" */
    private static final byte[] RFC_SEED =
        "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void 符合RFC6238的官方测试向量() {
        // 表格来自 RFC 6238 Appendix B（SHA-1 那一列，8 位码）
        assertCode("94287082", 59L);
        assertCode("07081804", 1111111109L);
        assertCode("14050471", 1111111111L);
        assertCode("89005924", 1234567890L);
        assertCode("69279037", 2000000000L);
        assertCode("65353130", 20000000000L);
    }

    private void assertCode(String expected, long epochSeconds) {
        long counter = epochSeconds / Totp.STEP.getSeconds();
        assertThat(Totp.code(RFC_SEED, counter, 8))
            .as("RFC 6238 在 T=%d 时的码", epochSeconds)
            .isEqualTo(expected);
    }

    @Test
    void Base32编解码能往返() {
        byte[] original = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        String encoded = Totp.base32Encode(original);

        assertThat(encoded)
            .as("这个值可以拿去和任何在线 Base32 工具对照")
            .isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decode(encoded)).isEqualTo(original);
    }

    @Test
    void 生成的密钥是合法的Base32并且每次都不同() {
        String a = Totp.newSecret();
        String b = Totp.newSecret();

        assertThat(a).hasSize(32).matches("[A-Z2-7]+");
        assertThat(a).isNotEqualTo(b);
        assertThat(Totp.base32Decode(a)).hasSize(20);
    }

    @Test
    void 当前的码能通过校验() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;

        String code = Totp.currentCode(secret, now);
        assertThat(Totp.verify(secret, code, now)).isNotEqualTo(-1L);
    }

    @Test
    void 前后一个时间片内也认() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;
        String code = Totp.currentCode(secret, now);

        // 用户手机慢了 30 秒，或者看到码之后过了半分钟才提交
        assertThat(Totp.verify(secret, code, now + 30)).as("晚 30 秒").isNotEqualTo(-1L);
        assertThat(Totp.verify(secret, code, now - 30)).as("早 30 秒").isNotEqualTo(-1L);
    }

    @Test
    void 超出窗口就不认了() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;
        String code = Totp.currentCode(secret, now);

        assertThat(Totp.verify(secret, code, now + 90))
            .as("过了三个时间片，必须拒绝")
            .isEqualTo(-1L);
    }

    @Test
    void 别人的密钥算出来的码不认() {
        long now = 1_700_000_000L;
        String mine = Totp.newSecret();
        String theirs = Totp.newSecret();

        assertThat(Totp.verify(mine, Totp.currentCode(theirs, now), now)).isEqualTo(-1L);
    }

    @Test
    void 乱七八糟的输入不会抛异常() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;

        assertThat(Totp.verify(secret, null, now)).isEqualTo(-1L);
        assertThat(Totp.verify(secret, "", now)).isEqualTo(-1L);
        assertThat(Totp.verify(secret, "abc", now)).isEqualTo(-1L);
        assertThat(Totp.verify(secret, "12345", now)).as("位数不对").isEqualTo(-1L);
        assertThat(Totp.verify(secret, "1234567", now)).as("位数不对").isEqualTo(-1L);
        assertThat(Totp.verify(null, "123456", now)).isEqualTo(-1L);
    }

    @Test
    void 输入里的空格会被忽略() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;
        String code = Totp.currentCode(secret, now);

        // 认证器 App 显示成 "123 456"，用户很可能连空格一起复制
        String spaced = code.substring(0, 3) + " " + code.substring(3);
        assertThat(Totp.verify(secret, spaced, now)).isNotEqualTo(-1L);
    }

    @Test
    void 校验返回的时间片编号可以用来防重放() {
        String secret = Totp.newSecret();
        long now = 1_700_000_000L;
        String code = Totp.currentCode(secret, now);

        long slot = Totp.verify(secret, code, now);
        assertThat(slot).isEqualTo(now / 30);

        // 同一个码在 25 秒后仍然有效（还在同一片），
        // 所以光靠「码对不对」挡不住重放 —— 必须记下用过的 slot
        assertThat(Totp.verify(secret, code, now + 25)).isEqualTo(slot);
    }

    @Test
    void 二维码内容符合otpauth规范() {
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        String uri = Totp.provisioningUri("Javalinky", "john@example.com", secret);

        assertThat(uri)
            .startsWith("otpauth://totp/Javalinky:john%40example.com?")
            .contains("secret=" + secret)
            .contains("issuer=Javalinky")
            .contains("digits=6")
            .contains("period=30");
    }
}
