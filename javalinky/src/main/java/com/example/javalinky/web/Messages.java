package com.example.javalinky.web;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * 界面文案的多语言查表（第 23 章）。
 *
 * 用 JDK 自带的 {@link ResourceBundle}，文案放在
 * src/main/resources/messages_*.properties 里。
 */
public final class Messages {

    private static final String BUNDLE = "messages";

    /** 本站支持的语言，按优先级排。没命中就用第一个 */
    public static final List<Locale> SUPPORTED = List.of(
        Locale.SIMPLIFIED_CHINESE,
        Locale.ENGLISH);

    private final ResourceBundle bundle;
    private final Locale locale;

    private Messages(Locale locale) {
        this.locale = locale;
        // 显式用 Control 关掉「找不到就回退到系统默认语言」这个行为 ——
        // 服务器的系统语言和用户想看什么毫无关系
        this.bundle = ResourceBundle.getBundle(BUNDLE, locale,
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    public static Messages of(Locale locale) {
        return new Messages(negotiate(List.of(locale)));
    }

    /**
     * 从 Accept-Language 头里挑一个我们支持的语言。
     * 自己解析这个头很容易写错，JDK 的 {@link Locale#lookup} 已经实现了 RFC 4647。
     */
    public static Locale fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) return Formats.DEFAULT_LOCALE;
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(header);
            Locale match = Locale.lookup(ranges, SUPPORTED);
            return match == null ? Formats.DEFAULT_LOCALE : match;
        } catch (IllegalArgumentException e) {
            // 畸形的头不该让整个请求挂掉
            return Formats.DEFAULT_LOCALE;
        }
    }

    /** 把候选语言收敛到我们真正支持的那几个之一 */
    public static Locale negotiate(List<Locale> preferred) {
        for (Locale candidate : preferred) {
            if (candidate == null) continue;
            for (Locale supported : SUPPORTED) {
                if (supported.getLanguage().equals(candidate.getLanguage())) return supported;
            }
        }
        return Formats.DEFAULT_LOCALE;
    }

    public Locale locale() {
        return locale;
    }

    /**
     * 取一条文案。<b>找不到时返回的是 key 本身</b>，不是抛异常 ——
     * 漏翻译一条不该让整个页面 500。
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** 带参数的文案：{0} 会被替换 */
    public String get(String key, Object... args) {
        String pattern = get(key);
        if (args == null || args.length == 0) return pattern;
        // MessageFormat 本身就会按 locale 格式化数字和日期
        return new MessageFormat(pattern, locale).format(args);
    }

    /** 给模板里做语言切换菜单用 */
    public static List<Locale> supported() {
        return Collections.unmodifiableList(SUPPORTED);
    }
}
