package com.example.javalinky.mail;

public record MailConfig(
    String host,
    int port,
    boolean startTls,
    String username,       // 不需要认证时传 null
    String password,
    String from,           // 如 "Javalinky <no-reply@javalinky.example>"
    String subjectPrefix   // 如 "[Javalinky] "
) {
    /** 开发默认值：对准本地 Mailpit */
    public static MailConfig fromEnv() {
        var env = System.getenv();
        return new MailConfig(
            env.getOrDefault("MAIL_HOST", "localhost"),
            Integer.parseInt(env.getOrDefault("MAIL_PORT", "1025")),
            Boolean.parseBoolean(env.getOrDefault("MAIL_STARTTLS", "false")),
            env.get("MAIL_USERNAME"),
            env.get("MAIL_PASSWORD"),
            env.getOrDefault("MAIL_FROM", "Javalinky <no-reply@javalinky.example>"),
            env.getOrDefault("MAIL_SUBJECT_PREFIX", "[Javalinky] ")
        );
    }
}
