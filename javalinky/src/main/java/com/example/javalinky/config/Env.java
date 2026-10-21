package com.example.javalinky.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置来源，优先级从高到低：
 *   1. 真正的环境变量（容器和 PaaS 用这个）
 *   2. 项目根目录的 .env 文件（本地开发用这个）
 *   3. 代码里写的默认值
 */
public final class Env {

    private final Map<String, String> dotenv;

    private Env(Map<String, String> dotenv) {
        this.dotenv = dotenv;
    }

    public static Env load() {
        return new Env(readDotenv(Path.of(".env")));
    }

    public String get(String key, String defaultValue) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        String fromFile = dotenv.get(key);
        if (fromFile != null && !fromFile.isBlank()) return fromFile;

        return defaultValue;
    }

    /** 必填项：缺了就直接启动失败，而不是在运行时才报莫名其妙的错 */
    public String require(String key) {
        String value = get(key, null);
        if (value == null) {
            throw new IllegalStateException(
                "缺少必需的配置项 " + key + "（请设置环境变量或写进 .env）");
        }
        return value;
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(get(key, String.valueOf(defaultValue)));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(get(key, String.valueOf(defaultValue)));
    }

    private static Map<String, String> readDotenv(Path path) {
        Map<String, String> map = new HashMap<>();
        if (!Files.exists(path)) return map;
        try {
            for (String line : Files.readAllLines(path)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // 去掉可能存在的引号
                if (value.length() >= 2
                        && (value.startsWith("\"") && value.endsWith("\"")
                         || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                map.put(key, value);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("读取 .env 失败", e);
        }
        return map;
    }
}
