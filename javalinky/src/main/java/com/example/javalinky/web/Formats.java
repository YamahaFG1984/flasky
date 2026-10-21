package com.example.javalinky.web;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * 按「看的人是谁」来格式化时间（第 23 章）。
 *
 * 存的一律是 {@link Instant}（= UTC 的一个时刻，没有时区概念）；
 * <b>时区只在渲染的那一刻才参与进来</b>。这条规矩一旦破坏，
 * 后面什么都对不上。
 */
public record Formats(ZoneId zone, Locale locale) {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    public static final Locale DEFAULT_LOCALE = Locale.SIMPLIFIED_CHINESE;

    public static Formats defaults() {
        return new Formats(DEFAULT_ZONE, DEFAULT_LOCALE);
    }

    /** 2026年3月14日 15:09 */
    public String dateTime(Instant at) {
        if (at == null) return "";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
            .withLocale(locale).withZone(zone).format(at);
    }

    /** 2026年3月14日 */
    public String date(Instant at) {
        if (at == null) return "";
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(locale).withZone(zone).format(at);
    }

    /** 机器读的那种：2026-03-14T07:09:00Z。给 &lt;time datetime&gt; 用 */
    public String iso(Instant at) {
        return at == null ? "" : at.toString();
    }

    /**
     * 「3 分钟前」。
     * 注意「昨天」「今天」这类判断必须<b>先转到用户时区再比日期</b> ——
     * 直接拿 Instant 相减得到的是绝对时长，不是日历上的天数。
     */
    public String relative(Instant at, Instant now) {
        if (at == null) return "";

        Duration elapsed = Duration.between(at, now);
        if (elapsed.isNegative()) return "刚刚";

        long seconds = elapsed.toSeconds();
        if (seconds < 60)   return "刚刚";
        if (seconds < 3600) return elapsed.toMinutes() + " 分钟前";

        // 跨不跨天是按<b>用户所在时区的日历</b>算的，不是按 24 小时
        LocalDate thatDay = at.atZone(zone).toLocalDate();
        LocalDate today   = now.atZone(zone).toLocalDate();

        if (thatDay.equals(today))                return elapsed.toHours() + " 小时前";
        if (thatDay.equals(today.minusDays(1)))   return "昨天";
        if (thatDay.isAfter(today.minusDays(7)))  return today.toEpochDay() - thatDay.toEpochDay() + " 天前";
        return date(at);
    }
}
