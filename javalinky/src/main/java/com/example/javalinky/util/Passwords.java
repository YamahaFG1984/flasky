package com.example.javalinky.util;

import at.favre.lib.crypto.bcrypt.BCrypt;

public final class Passwords {

    /**
     * 代价因子。每加 1，计算时间翻倍。
     * 12 在 2026 年的服务器上约 0.2–0.3 秒，是个合理的平衡点：
     * 用户登录时感觉不到，攻击者暴力破解的成本高得离谱。
     */
    private static final int COST = 12;

    /** bcrypt 只处理前 72 字节，超长的部分会被静默忽略 */
    private static final int MAX_BYTES = 72;

    public static String hash(String plaintext) {
        checkLength(plaintext);
        return BCrypt.withDefaults().hashToString(COST, plaintext.toCharArray());
    }

    public static boolean verify(String plaintext, String hash) {
        if (plaintext == null || hash == null || hash.isBlank()) return false;
        try {
            return BCrypt.verifyer().verify(plaintext.toCharArray(), hash).verified;
        } catch (IllegalArgumentException e) {
            return false;   // 哈希格式不对（比如数据库里是脏数据）
        }
    }

    private static void checkLength(String plaintext) {
        if (plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("密码过长（bcrypt 上限为 72 字节）");
        }
    }

    private Passwords() {}
}
