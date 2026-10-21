package com.example.javalinky.config;

public enum Profile {
    DEV,     // 本地开发
    TEST,    // 跑单元测试
    PROD;    // 生产

    public static Profile fromEnv(String value) {
        if (value == null || value.isBlank()) return DEV;
        return Profile.valueOf(value.trim().toUpperCase());
    }

    public boolean isDev()  { return this == DEV; }
    public boolean isTest() { return this == TEST; }
    public boolean isProd() { return this == PROD; }
}
