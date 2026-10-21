package com.example.javalinky;

import io.javalin.Javalin;

public class App {
    public static void main(String[] args) {
        // 下一章我们才真正启动服务器，这里只验证 Javalin 能被 import
        System.out.println("Javalin 已就位：" + Javalin.class.getName());
    }
}
