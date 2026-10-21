package com.example.javalinky.web;

import io.javalin.http.Context;
import java.util.ArrayList;
import java.util.List;

/** 一条一次性提示消息。level 直接用 Bootstrap 的样式名。 */
public record Flash(String level, String message) {

    private static final String KEY = "flash";

    public static void success(Context ctx, String msg) { add(ctx, "success", msg); }
    public static void info(Context ctx, String msg)    { add(ctx, "info", msg); }
    public static void warning(Context ctx, String msg) { add(ctx, "warning", msg); }
    public static void danger(Context ctx, String msg)  { add(ctx, "danger", msg); }

    private static void add(Context ctx, String level, String msg) {
        List<Flash> list = ctx.sessionAttribute(KEY);
        if (list == null) {
            list = new ArrayList<>();
            ctx.sessionAttribute(KEY, list);
        }
        list.add(new Flash(level, msg));
    }

    /** 取出全部消息并清空。相当于 Flask 的 get_flashed_messages()。 */
    public static List<Flash> consume(Context ctx) {
        List<Flash> list = ctx.consumeSessionAttribute(KEY);
        return list == null ? List.of() : list;
    }
}
