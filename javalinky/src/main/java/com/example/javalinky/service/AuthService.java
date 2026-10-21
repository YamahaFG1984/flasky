package com.example.javalinky.service;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.mail.MailService;
import com.example.javalinky.util.Tokens;
import java.time.Duration;

public class AuthService {

    public static final String PURPOSE_CONFIRM = "confirm";
    public static final String PURPOSE_RESET   = "reset-password";
    public static final String PURPOSE_EMAIL   = "change-email";

    private final Tokens tokens;
    private final MailService mail;

    public AuthService(AppConfig config, MailService mail) {
        this.tokens = new Tokens(config.secretKey());
        this.mail = mail;
    }

    public String confirmationToken(long userId) {
        return tokens.issue(PURPOSE_CONFIRM, userId, Duration.ofHours(24));
    }

    public String resetToken(long userId) {
        // 重置密码的窗口要短 —— 这是高危操作
        return tokens.issue(PURPOSE_RESET, userId, Duration.ofMinutes(30));
    }

    public String emailChangeToken(long userId, String newEmail) {
        return tokens.issue(PURPOSE_EMAIL, userId, Duration.ofHours(2),
                            "newEmail", newEmail);
    }

    public Tokens tokens() { return tokens; }
}
