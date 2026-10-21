package com.example.javalinky.web;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;

import java.nio.file.Path;
import java.util.Map;

public final class Templates {

    private static final Path JTE_ROOT = Path.of("src", "main", "jte");

    /** 网页和 HTML 邮件用：会做按上下文的 HTML 转义 */
    public static final TemplateEngine HTML = TemplateEngine.create(
        new DirectoryCodeResolver(JTE_ROOT),
        Path.of("jte-classes", "html"),
        ContentType.Html);

    /** 纯文本邮件用：不做任何转义 */
    public static final TemplateEngine TEXT = TemplateEngine.create(
        new DirectoryCodeResolver(JTE_ROOT),
        Path.of("jte-classes", "text"),
        ContentType.Plain);

    /** 渲染成字符串（而不是直接写进 HTTP 响应） */
    public static String render(TemplateEngine engine, String template, Map<String, Object> model) {
        var output = new StringOutput();
        engine.render(template, model, output);
        return output.toString();
    }

    private Templates() {}
}
