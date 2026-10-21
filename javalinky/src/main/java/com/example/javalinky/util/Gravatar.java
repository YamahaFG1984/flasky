package com.example.javalinky.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Gravatar {

    private static final String BASE = "https://www.gravatar.com/avatar/";

    /** 邮箱 → MD5 哈希（小写、去空格，这是 Gravatar 规定的） */
    public static String hash(String email) {
        if (email == null) return "";
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 居然没有 MD5", e);
        }
    }

    public static String url(String hash, int size) {
        return url(hash, size, "identicon");
    }

    public static String url(String hash, int size, String fallback) {
        return BASE + (hash == null ? "" : hash)
             + "?s=" + size + "&d=" + fallback + "&r=g";
    }

    private Gravatar() {}
}
