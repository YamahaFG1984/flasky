package com.example.javalinky.web;

import com.example.javalinky.model.User;
import io.javalin.http.Context;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * 决定「这个请求该用哪个时区、哪种语言」（第 23 章）。
 *
 * 优先级从高到低：
 *   1. 用户在设置里选的（登录用户）
 *   2. Cookie（匿名用户切换过语言）
 *   3. 浏览器的 Accept-Language 头
 *   4. 站点默认
 *
 * 算一次就缓存在 ctx 属性里，一个请求里可能渲染很多时间。
 */
public final class Localization {

    public static final String LOCALE_COOKIE = "lang";
    public static final String ZONE_COOKIE   = "tz";

    private static final String CTX_FORMATS  = "formats";
    private static final String CTX_MESSAGES = "messages";

    public static Formats formats(Context ctx) {
        Formats cached = ctx.attribute(CTX_FORMATS);
        if (cached != null) return cached;

        Formats formats = new Formats(resolveZone(ctx), resolveLocale(ctx));
        ctx.attribute(CTX_FORMATS, formats);
        return formats;
    }

    public static Messages messages(Context ctx) {
        Messages cached = ctx.attribute(CTX_MESSAGES);
        if (cached != null) return cached;

        Messages messages = Messages.of(resolveLocale(ctx));
        ctx.attribute(CTX_MESSAGES, messages);
        return messages;
    }

    public static ZoneId resolveZone(Context ctx) {
        User me = CurrentUser.get(ctx);
        ZoneId fromUser = parseZone(me == null ? null : me.getTimeZone());
        if (fromUser != null) return fromUser;

        ZoneId fromCookie = parseZone(ctx.cookie(ZONE_COOKIE));
        if (fromCookie != null) return fromCookie;

        return Formats.DEFAULT_ZONE;
    }

    public static Locale resolveLocale(Context ctx) {
        User me = CurrentUser.get(ctx);
        Locale fromUser = parseLocale(me == null ? null : me.getLocale());
        if (fromUser != null) return Messages.negotiate(List.of(fromUser));

        Locale fromCookie = parseLocale(ctx.cookie(LOCALE_COOKIE));
        if (fromCookie != null) return Messages.negotiate(List.of(fromCookie));

        return Messages.fromAcceptLanguage(ctx.header("Accept-Language"));
    }

    /**
     * 时区名来自用户输入或 Cookie，<b>一定要当成不可信的</b>。
     * ZoneId.of 对非法值会抛异常，不接住的话任何人都能用一个
     * tz=../../ 的 Cookie 把整站打成 500。
     */
    public static ZoneId parseZone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ZoneId.of(raw.trim());
        } catch (DateTimeException e) {
            return null;
        }
    }

    public static Locale parseLocale(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            Locale locale = Locale.forLanguageTag(raw.trim());
            return locale.getLanguage().isEmpty() ? null : locale;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Localization() {}
}
