package com.example.javalinky.web;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;

import java.nio.file.Path;
import java.util.Map;

/**
 * 一个 TemplateEngine 只有一个 ContentType，所以网页/HTML 邮件和纯文本邮件
 * 必须用两个引擎、两个源目录：
 *   src/main/jte       → ContentType.Html  （会按上下文转义）
 *   src/main/jte-text  → ContentType.Plain （不做任何转义）
 */
public final class Templates {

    private static final Path HTML_ROOT = Path.of("src", "main", "jte");
    private static final Path TEXT_ROOT = Path.of("src", "main", "jte-text");

    /** 纯文本邮件专用的包名，和 HTML 那套分开，预编译时才不会撞车 */
    private static final String TEXT_PACKAGE = "gg.jte.generated.text";

    /** 开发用：从目录读源文件，改了就生效 */
    public static TemplateEngine directory() {
        return TemplateEngine.create(
            new DirectoryCodeResolver(HTML_ROOT),
            Path.of("jte-classes", "html"),
            ContentType.Html);
    }

    /** 生产用：读 jar 里预编译好的模板类，启动快、不需要编译器（第 17 章） */
    public static TemplateEngine precompiled() {
        return TemplateEngine.createPrecompiled(ContentType.Html);
    }

    public static TemplateEngine textDirectory() {
        return TemplateEngine.create(
            new DirectoryCodeResolver(TEXT_ROOT),
            Path.of("jte-classes", "text"),
            ContentType.Plain);
    }

    public static TemplateEngine textPrecompiled() {
        return TemplateEngine.createPrecompiled(
            null, ContentType.Plain, Templates.class.getClassLoader(), TEXT_PACKAGE);
    }

    /** 渲染成字符串（而不是直接写进 HTTP 响应） */
    public static String render(TemplateEngine engine, String template, Map<String, Object> model) {
        var output = new StringOutput();
        engine.render(template, model, output);
        return output.toString();
    }

    private Templates() {}
}
