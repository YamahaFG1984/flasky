package com.example.javalinky.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * 基于时间的一次性密码（RFC 6238），也就是 Google Authenticator 里跳的那六位数（第 22 章）。
 *
 * 原理比想象中简单：
 *   1. 服务端和手机<b>共享一个随机密钥</b>（扫二维码的时候传过去的）
 *   2. 把「当前时间 / 30 秒」当成计数器
 *   3. HMAC-SHA1(密钥, 计数器) → 取 4 个字节 → 取模得到 6 位数
 * 两边算的是同一个东西，所以不需要任何通信。
 *
 * 自己实现是为了把原理讲清楚；生产项目直接用成熟库也完全可以。
 */
public final class Totp {

    /** 每 30 秒换一个码，这是事实标准（Google Authenticator 写死的） */
    public static final Duration STEP = Duration.ofSeconds(30);

    /** 六位数，同样是事实标准 */
    public static final int DIGITS = 6;

    /**
     * 允许前后各一个时间片。
     * 为什么要容错：用户手机的时钟可能差几秒，而且从看到码到按下提交也要时间。
     * 为什么只放一片：窗口越大，攻击者爆破和重放的机会越多。
     */
    public static final int WINDOW = 1;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /** 生成一个新密钥。20 字节 = 160 位，正好是 HMAC-SHA1 的块大小 */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** 当前这一刻该显示的码（主要给测试和演示用） */
    public static String currentCode(String base32Secret, long epochSeconds) {
        return code(base32Decode(base32Secret), epochSeconds / STEP.getSeconds(), DIGITS);
    }

    /**
     * 校验用户输入的码。
     * 返回匹配上的那个时间片编号（调用方要记下来防重放），没匹配上返回 -1。
     */
    public static long verify(String base32Secret, String input, long epochSeconds) {
        if (base32Secret == null || input == null) return -1;

        String cleaned = input.replaceAll("\\s", "");
        if (cleaned.length() != DIGITS) return -1;

        byte[] key = base32Decode(base32Secret);
        long now = epochSeconds / STEP.getSeconds();

        for (long t = now - WINDOW; t <= now + WINDOW; t++) {
            // 必须用常量时间比较 —— 普通的 equals 会在第一个不同的字符就返回，
            // 攻击者能通过计时差异一位一位地猜出正确的码
            if (constantTimeEquals(code(key, t, DIGITS), cleaned)) return t;
        }
        return -1;
    }

    /**
     * 给认证器 App 扫的二维码内容。
     * issuer 会显示在 App 里，让用户知道这是哪个网站的。
     */
    public static String provisioningUri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label
             + "?secret=" + base32Secret
             + "&issuer=" + enc(issuer)
             + "&algorithm=SHA1"
             + "&digits=" + DIGITS
             + "&period=" + STEP.getSeconds();
    }

    // ---------- 算法本体 ----------

    /** 暴露出来是为了能拿 RFC 6238 的测试向量直接对 */
    public static String code(byte[] key, long counter, int digits) {
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {          // 计数器按大端写成 8 字节
            data[i] = (byte) (counter & 0xFF);
            counter >>>= 8;
        }

        byte[] hash;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            hash = mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JVM 居然没有 HmacSHA1", e);
        }

        // 「动态截断」：用最后一个字节的低 4 位决定从哪里取 4 个字节。
        // 这一步是为了让结果均匀，不要总是取同一段。
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset]     & 0x7F) << 24)
                   | ((hash[offset + 1] & 0xFF) << 16)
                   | ((hash[offset + 2] & 0xFF) << 8)
                   |  (hash[offset + 3] & 0xFF);

        int mod = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", binary % mod);
    }

    // ---------- Base32（RFC 4648，认证器 App 用的就是它）----------

    public static String base32Encode(byte[] data) {
        var sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 0x1F));
        return sb.toString();
    }

    public static byte[] base32Decode(String s) {
        String clean = s.replaceAll("[=\\s]", "").toUpperCase();
        var out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("不是合法的 Base32：" + c);
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8),
            b.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private Totp() {}
}
