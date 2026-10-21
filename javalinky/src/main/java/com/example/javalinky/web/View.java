package com.example.javalinky.web;

import io.javalin.http.Context;
import java.util.HashMap;
import java.util.Map;

public final class View {

    /** 渲染页面，自动补上 layout。pairs 是 "键", 值, "键", 值 … */
    public static void render(Context ctx, String template, String title, Object... pairs) {
        Map<String, Object> model = new HashMap<>();
        model.put("layout", Layout.of(title));
        for (int i = 0; i < pairs.length; i += 2) {
            model.put((String) pairs[i], pairs[i + 1]);   // HashMap 允许 null 值
        }
        ctx.render(template, model);
    }

    private View() {}
}
