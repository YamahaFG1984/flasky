package com.example.javalinky.mail;

import com.example.javalinky.web.Templates;
import java.util.Map;

/** 业务语义的邮件发送入口。处理器只调这里的方法，不碰 Mailer。 */
public class MailService {

    private final Mailer mailer;
    private final String siteBaseUrl;   // 如 "http://localhost:7070"

    public MailService(Mailer mailer, String siteBaseUrl) {
        this.mailer = mailer;
        this.siteBaseUrl = siteBaseUrl;
    }

    public void sendConfirmation(String to, String username, String token) {
        var model = Map.<String, Object>of(
            "username", username,
            "confirmUrl", siteBaseUrl + "/auth/confirm/" + token
        );
        mailer.send(to, "确认你的账号",
            Templates.render(Templates.TEXT, "mail/confirm.txt.jte", model),
            Templates.render(Templates.HTML, "mail/confirm.html.jte", model));
    }

    public void sendPasswordReset(String to, String username, String token) {
        var model = Map.<String, Object>of(
            "username", username,
            "resetUrl", siteBaseUrl + "/auth/reset/" + token
        );
        mailer.send(to, "重置你的密码",
            Templates.render(Templates.TEXT, "mail/reset.txt.jte", model),
            Templates.render(Templates.HTML, "mail/reset.html.jte", model));
    }

    /** 第 17 章：有新用户注册时通知管理员 */
    public void notifyAdminNewUser(String adminEmail, String username) {
        var model = Map.<String, Object>of("username", username);
        mailer.send(adminEmail, "新用户注册",
            Templates.render(Templates.TEXT, "mail/new_user.txt.jte", model),
            Templates.render(Templates.HTML, "mail/new_user.html.jte", model));
    }
}
