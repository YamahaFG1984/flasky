package com.example.javalinky;

import com.example.javalinky.web.Formats;
import com.example.javalinky.web.Localization;
import com.example.javalinky.web.Messages;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时区和语言的坑几乎都在<b>边界</b>上：夏令时切换、跨日、
 * 畸形的 Accept-Language、用户填了个不存在的时区。
 * 正常路径反而很难出错。
 */
class LocalizationTest {

    // ---------- 时区 ----------

    @Test
    void 同一个时刻在不同时区显示成不同的日期() {
        // 2026-01-01T02:00Z = 北京时间 1 月 1 日 10:00，但纽约还是 12 月 31 日 21:00
        Instant moment = Instant.parse("2026-01-01T02:00:00Z");

        var shanghai = new Formats(ZoneId.of("Asia/Shanghai"), Locale.SIMPLIFIED_CHINESE);
        var newYork  = new Formats(ZoneId.of("America/New_York"), Locale.SIMPLIFIED_CHINESE);

        assertThat(shanghai.date(moment)).contains("2026").contains("1");
        assertThat(newYork.date(moment))
            .as("跨年这种事，看的人在哪决定了他看到哪一年")
            .contains("2025");
    }

    @Test
    void 夏令时切换当天只有23小时() {
        // 美国 2026 年 3 月 8 日进入夏令时：本地时间 2:00 直接跳到 3:00
        ZoneId ny = ZoneId.of("America/New_York");

        ZonedDateTime before = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, ny);
        ZonedDateTime after  = ZonedDateTime.of(2026, 3, 9, 0, 30, 0, 0, ny);

        assertThat(java.time.Duration.between(before, after).toHours())
            .as("日历上过了一天，但只过了 23 小时 —— 所以『一天 = 86400 秒』是错的")
            .isEqualTo(23);
    }

    @Test
    void 不存在的本地时间会被调整而不是抛异常() {
        ZoneId ny = ZoneId.of("America/New_York");
        // 2026-03-08 02:30 在纽约<b>根本不存在</b>（时钟从 2:00 跳到 3:00）
        ZonedDateTime ghost = ZonedDateTime.of(2026, 3, 8, 2, 30, 0, 0, ny);

        assertThat(ghost.getHour())
            .as("java.time 会把它顺延到 3:30，不会报错 —— 所以这种 bug 很难发现")
            .isEqualTo(3);
    }

    @Test
    void 跨日判断要按用户时区算而不是按24小时() {
        ZoneId ny = ZoneId.of("America/New_York");
        var formats = new Formats(ny, Locale.SIMPLIFIED_CHINESE);

        // 纽约时间 1 月 1 日 23:00 和 1 月 2 日 01:00，只差 2 小时但<b>跨了一天</b>
        Instant lateNight = ZonedDateTime.of(2026, 1, 1, 23, 0, 0, 0, ny).toInstant();
        Instant nextDay   = ZonedDateTime.of(2026, 1, 2, 1, 0, 0, 0, ny).toInstant();

        assertThat(formats.relative(lateNight, nextDay))
            .as("只隔 2 小时，但日历上是昨天")
            .isEqualTo("昨天");
    }

    @Test
    void 相对时间的几个档位() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        var formats = new Formats(zone, Locale.SIMPLIFIED_CHINESE);
        Instant now = ZonedDateTime.of(2026, 6, 15, 12, 0, 0, 0, zone).toInstant();

        assertThat(formats.relative(now.minusSeconds(10), now)).isEqualTo("刚刚");
        assertThat(formats.relative(now.minusSeconds(300), now)).isEqualTo("5 分钟前");
        assertThat(formats.relative(now.minusSeconds(3 * 3600), now)).isEqualTo("3 小时前");
        assertThat(formats.relative(null, now)).as("null 不该炸").isEmpty();
    }

    @Test
    void 未来的时间不会显示成负数() {
        var formats = Formats.defaults();
        Instant now = Instant.parse("2026-06-15T12:00:00Z");

        assertThat(formats.relative(now.plusSeconds(60), now))
            .as("时钟漂移会让服务器看到『未来』的时间戳")
            .isEqualTo("刚刚");
    }

    @Test
    void 非法的时区名被挡住而不是抛异常() {
        assertThat(Localization.parseZone("Asia/Shanghai")).isNotNull();
        assertThat(Localization.parseZone("不存在的/时区")).isNull();
        assertThat(Localization.parseZone("../../etc/passwd")).isNull();
        assertThat(Localization.parseZone("")).isNull();
        assertThat(Localization.parseZone(null)).isNull();
    }

    @Test
    void 偏移量不等于时区() {
        // "+08:00" 是个合法的 ZoneId，但它<b>不懂夏令时</b>
        var offset = Localization.parseZone("+08:00");
        assertThat(offset).isNotNull();

        var realZone = ZoneId.of("America/New_York");
        var fixed    = ZoneId.of("-05:00");    // 纽约的冬季偏移

        Instant summer = Instant.parse("2026-07-01T12:00:00Z");
        assertThat(summer.atZone(realZone).getHour())
            .as("真时区懂夏令时，夏天是 -04:00")
            .isNotEqualTo(summer.atZone(fixed).getHour());
    }

    // ---------- 语言 ----------

    @Test
    void 按AcceptLanguage挑语言() {
        assertThat(Messages.fromAcceptLanguage("en-US,en;q=0.9")).isEqualTo(Locale.ENGLISH);
        assertThat(Messages.fromAcceptLanguage("zh-CN,zh;q=0.9,en;q=0.8"))
            .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    }

    @Test
    void 不支持的语言回退到默认() {
        assertThat(Messages.fromAcceptLanguage("fr-FR,fr;q=0.9"))
            .isEqualTo(Formats.DEFAULT_LOCALE);
    }

    @Test
    void 畸形的AcceptLanguage不会让请求挂掉() {
        assertThat(Messages.fromAcceptLanguage("")).isEqualTo(Formats.DEFAULT_LOCALE);
        assertThat(Messages.fromAcceptLanguage(null)).isEqualTo(Formats.DEFAULT_LOCALE);
        assertThat(Messages.fromAcceptLanguage("这不是一个合法的头 ;;;; q=abc"))
            .isEqualTo(Formats.DEFAULT_LOCALE);
    }

    @Test
    void 两种语言的文案都能取到() {
        assertThat(Messages.of(Locale.SIMPLIFIED_CHINESE).get("nav.home")).isEqualTo("首页");
        assertThat(Messages.of(Locale.ENGLISH).get("nav.home")).isEqualTo("Home");
    }

    @Test
    void 漏翻译时返回key而不是抛异常() {
        assertThat(Messages.of(Locale.ENGLISH).get("nav.这个键不存在"))
            .as("漏一条文案不该让整个页面 500")
            .isEqualTo("nav.这个键不存在");
    }

    @Test
    void 单复数按数量选不同的句子() {
        var en = Messages.of(Locale.ENGLISH);
        assertThat(en.get("post.comments", 0)).isEqualTo("No comments");
        assertThat(en.get("post.comments", 1)).isEqualTo("1 comment");
        assertThat(en.get("post.comments", 5)).isEqualTo("5 comments");

        var zh = Messages.of(Locale.SIMPLIFIED_CHINESE);
        assertThat(zh.get("post.comments", 0)).isEqualTo("还没有评论");
        assertThat(zh.get("post.comments", 5)).isEqualTo("5 条评论");
    }

    @Test
    void 文案文件是UTF8读的() {
        // Java 9 起 PropertyResourceBundle 默认按 UTF-8 读，
        // 不用再做 native2ascii。这条测试就是在钉住这件事。
        assertThat(Messages.of(Locale.SIMPLIFIED_CHINESE).get("nav.logout"))
            .isEqualTo("登出");
    }

    @Test
    void 非法的语言标签被挡住() {
        assertThat(Localization.parseLocale("zh-CN")).isNotNull();
        assertThat(Localization.parseLocale("")).isNull();
        assertThat(Localization.parseLocale(null)).isNull();
        assertThat(Localization.parseLocale("!!!")).isNull();
    }
}
