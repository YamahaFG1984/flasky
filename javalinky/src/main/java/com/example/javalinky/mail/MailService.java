package com.example.javalinky.mail;

import com.example.javalinky.web.Templates;
import gg.jte.TemplateEngine;

import java.util.Map;

/** 业务语义的邮件发送入口。处理器只调这里的方法，不碰 Mailer。 */
public class MailService {

    private final Mailer mailer;
    private final String siteBaseUrl;       // 如 "http://localhost:7070"
    private final TemplateEngine html;      // ContentType.Html
    private final TemplateEngine text;      // ContentType.Plain

    public MailService(Mailer mailer, String siteBaseUrl,
                       TemplateEngine html, TemplateEngine text) {
        this.mailer = mailer;
        this.siteBaseUrl = siteBaseUrl;
        this.html = html;
        this.text = text;
    }

    public void sendConfirmation(String to, String username, String token) {
        var model = Map.<String, Object>of(
            "username", username,
            "confirmUrl", siteBaseUrl + "/auth/confirm/" + token
        );
        send(to, "确认你的账号", "mail/confirm", model);
    }

    public void sendPasswordReset(String to, String username, String token) {
        var model = Map.<String, Object>of(
            "username", username,
            "resetUrl", siteBaseUrl + "/auth/reset/" + token
        );
        send(to, "重置你的密码", "mail/reset", model);
    }

    public void sendEmailChange(String to, String username, String token) {
        var model = Map.<String, Object>of(
            "username", username,
            "confirmUrl", siteBaseUrl + "/auth/change-email/" + token
        );
        send(to, "确认你的新邮箱", "mail/change_email", model);
    }

    /** 第 17 章：有新用户注册时通知管理员 */
    public void notifyAdminNewUser(String adminEmail, String username) {
        send(adminEmail, "新用户注册", "mail/new_user",
             Map.<String, Object>of("username", username));
    }

    /** 两个版本用同一个基名：mail/x.txt.jte 和 mail/x.html.jte */
    private void send(String to, String subject, String baseName, Map<String, Object> model) {
        mailer.send(to, subject,
            Templates.render(text, baseName + ".txt.jte", model),
            Templates.render(html, baseName + ".html.jte", model));
    }
}
