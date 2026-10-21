package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.model.User;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.NameForm;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.Map;

import static io.javalin.apibuilder.ApiBuilder.*;

/** 站点主体的路由。相当于 Flask 的 main 蓝本。 */
public class MainRoutes implements RouteModule {

    private final AppConfig cfg;
    private final Services services;

    public MainRoutes(AppConfig cfg, Services services) {
        this.cfg = cfg;
        this.services = services;
    }

    @Override
    public void register(JavalinConfig config) {
        config.routes.apiBuilder(() -> {
            get("/", this::index);
            post("/", this::submitName);
        });
    }

    private void index(Context ctx) {
        String name = ctx.sessionAttribute("name");
        Boolean known = ctx.sessionAttribute("known");
        View.render(ctx, "index.jte", "首页",
            "form", FormView.empty(),
            "page", new IndexPage(name, Boolean.TRUE.equals(known)));
    }

    private void submitName(Context ctx) {
        var form = new NameForm(ctx.formParam("name"));
        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            View.render(ctx, "index.jte", "首页",
                "form", new FormView(
                    Map.of("name", form.name() == null ? "" : form.name()), errors),
                "page", new IndexPage(null, false));
            return;
        }

        var users = services.users(ctx);
        boolean known = users.findByUsername(form.name()).isPresent();
        if (!known) {
            users.save(new User(form.name() + "@example.com", form.name()));
            services.mail().notifyAdminNewUser(cfg.adminEmail(), form.name());
        }

        ctx.sessionAttribute("name", form.name());
        ctx.sessionAttribute("known", known);
        ctx.redirect(Routes.INDEX);     // Post/Redirect/Get
    }
}
