package com.example.javalinky.mail;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final MailConfig config;
    private final Session session;
    private final ExecutorService executor;

    public Mailer(MailConfig config) {
        this.config = config;
        this.session = buildSession(config);
        // Java 21 的虚拟线程：每封邮件一个线程，廉价到可以随便创建
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    private static Session buildSession(MailConfig c) {
        Properties props = new Properties();
        props.put("mail.smtp.host", c.host());
        props.put("mail.smtp.port", String.valueOf(c.port()));
        props.put("mail.smtp.starttls.enable", String.valueOf(c.startTls()));
        props.put("mail.smtp.connectiontimeout", "10000");  // 别让它无限期挂着
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        if (c.username() == null || c.username().isBlank()) {
            props.put("mail.smtp.auth", "false");
            return Session.getInstance(props);
        }

        props.put("mail.smtp.auth", "true");
        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(c.username(), c.password());
            }
        });
    }

    /** 同步发送：会阻塞到 SMTP 服务器返回（几百毫秒到几秒） */
    public void sendNow(String to, String subject, String textBody, String htmlBody) {
        if (!config.enabled()) {           // 测试环境：不真的连 SMTP
            log.info("邮件已禁用，跳过：to={} subject={}", to, subject);
            return;
        }
        try {
            var message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(config.subjectPrefix() + subject, "UTF-8");

            // multipart/alternative：同一封信的两个版本，客户端自己挑
            var textPart = new MimeBodyPart();
            textPart.setText(textBody, StandardCharsets.UTF_8.name());

            var htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

            var multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(textPart);   // 顺序重要：后面的优先级更高
            multipart.addBodyPart(htmlPart);
            message.setContent(multipart);

            Transport.send(message);
            log.info("邮件已投递给 SMTP：to={} subject={}", to, subject);

        } catch (MessagingException e) {
            // 故意不往上抛：发邮件失败不该让用户的注册流程失败
            log.error("发送邮件失败：to={} subject={}", to, subject, e);
        }
    }

    /** 异步发送：立刻返回，真正的发送在后台线程里跑 */
    public void send(String to, String subject, String textBody, String htmlBody) {
        executor.submit(() -> sendNow(to, subject, textBody, htmlBody));
    }

    public void close() {
        executor.close();   // Java 19+：等待已提交的任务跑完
    }
}
