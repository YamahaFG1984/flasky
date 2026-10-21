package com.example.javalinky;

import com.example.javalinky.config.AppConfig;

public class App {
    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        JavalinkyApp.create(config).start(config.port());
    }
}
