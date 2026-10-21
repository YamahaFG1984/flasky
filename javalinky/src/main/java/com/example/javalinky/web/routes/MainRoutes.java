package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.AdminEditProfileForm;
import com.example.javalinky.web.form.EditProfileForm;
import com.example.javalinky.web.form.NameForm;
import com.example.javalinky.web.page.UserProfilePage;
import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

import java.util.*;

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

            // 公开页面
            get("/", this::index);
            post("/", this::submitName);
            get("/user/{username}", this::userProfile);

            // 必须登录
            get("/edit-profile",  this::editProfileForm, Access.AUTHENTICATED);
            post("/edit-profile", this::editProfile,     Access.AUTHENTICATED);

            // 管理员专属：整个前缀挂一个角色
            path("/admin", List.of(Permission.ADMIN), () -> {
                get("/edit-profile/{id}",  this::adminEditProfileForm);
                post("/edit-profile/{id}", this::adminEditProfile);
            });
        });
    }

    // ---------- 首页 ----------

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
                "form", new FormView(Map.of("name", orEmpty(form.name())), errors),
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

    // ---------- 用户主页 ----------

    private void userProfile(Context ctx) {
        var users = services.users(ctx);
        User user = users.findByUsername(ctx.pathParam("username"))
            .orElseThrow(() -> new NotFoundResponse("没有这个用户"));

        User viewer = CurrentUser.get(ctx);

        var page = UserProfilePage.of(
            user,
            viewer != null && viewer.isAdministrator(),
            viewer != null && viewer.getId().equals(user.getId()),
            0, 0,                       // 文章数、评论数，第 11、13 章填上
            0, 0,                       // 关注数，第 12 章填上
            viewer != null && viewer.can(Permission.FOLLOW)
                && !viewer.getId().equals(user.getId()),
            false, false);              // 关注状态，第 12 章填上

        View.render(ctx, "user.jte", user.getUsername(), "page", page);
    }

    // ---------- 编辑自己的资料 ----------

    private void editProfileForm(Context ctx) {
        User user = CurrentUser.require(ctx);
        // GET 时把当前值回填进表单
        var values = new HashMap<String, String>();
        values.put("name", orEmpty(user.getName()));
        values.put("location", orEmpty(user.getLocation()));
        values.put("aboutMe", orEmpty(user.getAboutMe()));

        View.render(ctx, "edit_profile.jte", "编辑资料",
                    "form", new FormView(values, Map.of()),
                    "admin", false,
                    "targetUser", null);
    }

    private void editProfile(Context ctx) {
        User user = CurrentUser.require(ctx);
        var form = new EditProfileForm(
            ctx.formParam("name"),
            ctx.formParam("location"),
            ctx.formParam("aboutMe"));

        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            View.render(ctx, "edit_profile.jte", "编辑资料",
                        "form", new FormView(formValues(form), errors),
                        "admin", false, "targetUser", null);
            return;
        }

        user.setName(blankToNull(form.name()));
        user.setLocation(blankToNull(form.location()));
        user.setAboutMe(blankToNull(form.aboutMe()));
        // 不需要 save() —— 托管对象，事务提交时自动 UPDATE

        Flash.success(ctx, "资料已更新。");
        ctx.redirect(Routes.user(user.getUsername()));
    }

    // ---------- 管理员编辑别人的资料 ----------

    private void adminEditProfileForm(Context ctx) {
        User target = targetUser(ctx);
        var values = new HashMap<String, String>();
        values.put("email", target.getEmail());
        values.put("username", target.getUsername());
        if (target.isConfirmed()) values.put("confirmed", "1");
        if (target.getRole() != null) values.put("roleId", String.valueOf(target.getRole().getId()));
        values.put("name", orEmpty(target.getName()));
        values.put("location", orEmpty(target.getLocation()));
        values.put("aboutMe", orEmpty(target.getAboutMe()));

        View.render(ctx, "edit_profile.jte", "编辑资料（管理员）",
                    "form", new FormView(values, Map.of()),
                    "admin", true,
                    "targetUser", target,
                    "roles", services.roles(ctx).findAll());
    }

    private void adminEditProfile(Context ctx) {
        var users = services.users(ctx);
        var roles = services.roles(ctx);
        User target = targetUser(ctx);

        var form = new AdminEditProfileForm(
            ctx.formParam("email"),
            ctx.formParam("username"),
            ctx.formParam("confirmed") != null,
            ctx.formParamAsClass("roleId", Long.class).getOrNull(),
            ctx.formParam("name"),
            ctx.formParam("location"),
            ctx.formParam("aboutMe"));

        var errors = new LinkedHashMap<String, List<String>>(Forms.validate(form));

        // 唯一性检查：排除“自己”（原书的 validate_email 也是这个逻辑）
        String email = orEmpty(form.email()).toLowerCase();
        if (!errors.containsKey("email")
                && !email.equalsIgnoreCase(target.getEmail())
                && users.emailExists(email)) {
            errors.put("email", List.of("这个邮箱已被其他账号使用"));
        }
        if (!errors.containsKey("username")
                && !orEmpty(form.username()).equals(target.getUsername())
                && users.usernameExists(form.username())) {
            errors.put("username", List.of("这个用户名已被占用"));
        }

        if (!errors.isEmpty()) {
            View.render(ctx, "edit_profile.jte", "编辑资料（管理员）",
                        "form", new FormView(adminFormValues(form), errors),
                        "admin", true,
                        "targetUser", target,
                        "roles", roles.findAll());
            return;
        }

        boolean emailChanged = !email.equalsIgnoreCase(target.getEmail());
        target.setEmail(email);
        target.setUsername(form.username());
        target.setConfirmed(form.confirmed());
        target.setRole(roles.findById(form.roleId())
            .orElseThrow(() -> new BadRequestResponse("角色不存在")));
        target.setName(blankToNull(form.name()));
        target.setLocation(blankToNull(form.location()));
        target.setAboutMe(blankToNull(form.aboutMe()));

        if (emailChanged) {
            target.setAvatarHash(null);   // 头像哈希要按新邮箱重算
        }

        Flash.success(ctx, "已更新 " + target.getUsername() + " 的资料。");
        ctx.redirect(Routes.user(target.getUsername()));
    }

    private User targetUser(Context ctx) {
        long targetId = ctx.pathParamAsClass("id", Long.class).get();
        return services.users(ctx).findById(targetId)
            .orElseThrow(() -> new NotFoundResponse("没有这个用户"));
    }

    // ---------- 小工具 ----------

    private static Map<String, String> formValues(EditProfileForm form) {
        var values = new HashMap<String, String>();
        values.put("name", orEmpty(form.name()));
        values.put("location", orEmpty(form.location()));
        values.put("aboutMe", orEmpty(form.aboutMe()));
        return values;
    }

    private static Map<String, String> adminFormValues(AdminEditProfileForm form) {
        var values = new HashMap<String, String>();
        values.put("email", orEmpty(form.email()));
        values.put("username", orEmpty(form.username()));
        if (form.confirmed()) values.put("confirmed", "1");
        if (form.roleId() != null) values.put("roleId", String.valueOf(form.roleId()));
        values.put("name", orEmpty(form.name()));
        values.put("location", orEmpty(form.location()));
        values.put("aboutMe", orEmpty(form.aboutMe()));
        return values;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 空字符串存成 null，保持“没填”的语义统一 */
    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
