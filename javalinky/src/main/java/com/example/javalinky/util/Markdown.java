package com.example.javalinky.util;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

import java.util.List;

public final class Markdown {

    private static final Parser PARSER = Parser.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();

    /**
     * 允许的标签白名单。原则是：只放进展示文章确实需要的标签，
     * 其他一律删掉。不要写黑名单 —— 你永远列不全攻击者的花样。
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
        .allowElements(
            "p", "br", "hr",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "strong", "em", "b", "i", "s", "del",
            "ul", "ol", "li",
            "blockquote", "code", "pre",
            "a", "table", "thead", "tbody", "tr", "th", "td")
        // 只允许这几种链接协议 —— 挡掉 javascript: 和 data:
        .allowUrlProtocols("http", "https", "mailto")
        .allowAttributes("href").onElements("a")
        // 外链加上 rel="nofollow noopener"，防止被利用做 SEO 和窗口劫持
        .requireRelNofollowOnLinks()
        // 代码块的语言标记（<code class="language-java">）
        .allowAttributes("class").matching(
            java.util.regex.Pattern.compile("^language-[a-z0-9+#-]+$")).onElements("code")
        .toFactory();

    /** 文章用：渲染 Markdown 然后清洗 */
    public static String toSafeHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        String dirty = RENDERER.render(PARSER.parse(markdown));
        return POLICY.sanitize(dirty);
    }

    /** 评论用：标签更少（第 13 章） */
    private static final PolicyFactory COMMENT_POLICY = new HtmlPolicyBuilder()
        .allowElements("p", "br", "strong", "em", "b", "i", "code", "a")
        .allowUrlProtocols("http", "https", "mailto")
        .allowAttributes("href").onElements("a")
        .requireRelNofollowOnLinks()
        .toFactory();

    public static String toSafeCommentHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        return COMMENT_POLICY.sanitize(RENDERER.render(PARSER.parse(markdown)));
    }

    private Markdown() {}
}
