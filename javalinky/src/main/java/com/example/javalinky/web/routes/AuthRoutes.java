package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.model.User;
import com.example.javalinky.service.AuthService;
import com.example.javalinky.util.Tokens;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.*;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;

import java.util.*;

import static io.javalin.apibuilder.ApiBuilder.*;

/** 认证相关的路由。相当于 Flask 的 auth 蓝本。 */
public class AuthRoutes implements RouteModule {

    private final AppConfig cfg;
    private final Services services;

    public AuthRoutes(AppConfig cfg, Services services) {
        this.cfg = cfg;
        this.services = services;
    }

    @Override
    public void register(JavalinConfig config) {
        config.routes.apiBuilder(() -> path("/auth", () -> {
            get("/login",  this::loginForm);
            post("/login", this::login);
            post("/logout", this::logout);

            get("/register",  this::registerForm);
            post("/register", this::register);

            get("/confirm/{token}", this::confirm);
            get("/confirm",         this::resendConfirmation);
            get("/unconfirmed",     this::unconfirmed);

            get("/change-password",  this::changePasswordForm);
            post("/change-password", this::changePassword);

            get("/reset",  this::resetRequestForm);
            post("/reset", this::requestPasswordReset);
            get("/reset/{token}",  this::resetForm);
            post("/reset/{token}", this::doPasswordReset);

            get("/change-email",  this::emailChangeForm);
            post("/change-email", this::requestEmailChange);
            get("/change-email/{token}", this::confirmEmailChange);
        }));

        installUnconfirmedGuard(config);
    }

    // ---------- 登录/登出 ----------

    private void loginForm(Context ctx) {
        if (CurrentUser.isAuthenticated(ctx)) {
            ctx.redirect(Routes.INDEX);
            return;
        }
        View.render(ctx, "auth/login.jte", "登录", "form", FormView.empty());
    }

    private void login(Context ctx) {
        var form = new LoginForm(
            ctx.formParam("email"),
            ctx.formParam("password"),
            ctx.formParam("remember") != null);

        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            renderLoginWithErrors(ctx, form, errors);
            return;
        }

        var users = services.users(ctx);
        User user = users.findByEmail(form.email().toLowerCase()).orElse(null);

        if (user == null || !user.checkPassword(form.password())) {
            // 注意：不区分“用户不存在”和“密码错误”
            Flash.danger(ctx, "邮箱或密码错误。");
            renderLoginWithErrors(ctx, form, Map.of());
            return;
        }

        CurrentUser.login(ctx, user);
        Flash.success(ctx, "欢迎回来，" + user.getUsername() + "！");
        ctx.redirect(safeNext(ctx, Routes.INDEX));
    }

    private void logout(Context ctx) {
        CurrentUser.logout(ctx);
        // invalidate() 之后 Flash 也没了，所以这条消息要重新放进新会话
        Flash.info(ctx, "你已登出。");
        ctx.redirect(Routes.INDEX);
    }

    private void renderLoginWithErrors(Context ctx, LoginForm form,
                                       Map<String, List<String>> errors) {
        var values = new HashMap<String, String>();
        values.put("email", nullToEmpty(form.email()));
        if (form.remember()) values.put("remember", "1");
        View.render(ctx, "auth/login.jte", "登录",
                    "form", new FormView(values, errors));
    }

    // ---------- 注册 ----------

    private void registerForm(Context ctx) {
        if (CurrentUser.isAuthenticated(ctx)) {
            ctx.redirect(Routes.INDEX);
            return;
        }
        View.render(ctx, "auth/register.jte", "注册", "form", FormView.empty());
    }

    private void register(Context ctx) {
        var form = new RegisterForm(
            ctx.formParam("email"),
            ctx.formParam("username"),
            ctx.formParam("password"),
            ctx.formParam("password2"));

        // 1. 注解能查的先查
        var errors = new LinkedHashMap<String, List<String>>(Forms.validate(form));

        // 2. 需要查数据库的校验（注解做不到），相当于原书的 validate_email/validate_username
        var users = services.users(ctx);
        String email = nullToEmpty(form.email()).toLowerCase();
        if (!errors.containsKey("email") && users.emailExists(email)) {
            errors.put("email", List.of("这个邮箱已经注册过了"));
        }
        if (!errors.containsKey("username") && users.usernameExists(form.username())) {
            errors.put("username", List.of("这个用户名已被占用"));
        }

        if (!errors.isEmpty()) {
            var values = new HashMap<String, String>();
            values.put("email", email);
            values.put("username", nullToEmpty(form.username()));
            View.render(ctx, "auth/register.jte", "注册",
                        "form", new FormView(values, errors));
            return;
        }

        // 3. 创建用户（第 9 章会在这里顺手分配默认角色）
        var user = new User(email, form.username());
        user.setPassword(form.password());
        users.save(user);

        // 4. 发确认邮件。注意：令牌要用 user.getId()，所以必须先 flush 拿到主键
        Db.em(ctx).flush();
        String token = services.auth().confirmationToken(user.getId());
        services.mail().sendConfirmation(user.getEmail(), user.getUsername(), token);

        Flash.info(ctx, "确认邮件已发送，请查收邮箱完成注册。");
        ctx.redirect(Routes.LOGIN);
    }

    // ---------- 确认账号 ----------

    private void confirm(Context ctx) {
        var jwt = services.auth().tokens()
            .verify(ctx.pathParam("token"), AuthService.PURPOSE_CONFIRM);

        if (jwt.isEmpty()) {
            Flash.danger(ctx, "确认链接无效或已过期，请重新申请。");
            ctx.redirect(Routes.INDEX);
            return;
        }

        long userId = Tokens.userIdOf(jwt.get());
        User user = services.users(ctx).findById(userId).orElse(null);
        if (user == null) {
            Flash.danger(ctx, "确认链接无效。");
            ctx.redirect(Routes.INDEX);
            return;
        }

        if (user.isConfirmed()) {
            Flash.info(ctx, "这个账号已经确认过了。");
        } else {
            user.setConfirmed(true);        // 托管对象，改字段即入库
            Flash.success(ctx, "账号确认成功，欢迎加入 Javalinky！");
        }
        ctx.redirect(Routes.INDEX);
    }

    private void resendConfirmation(Context ctx) {
        User user = CurrentUser.require(ctx);
        if (user.isConfirmed()) {
            ctx.redirect(Routes.INDEX);
            return;
        }
        String token = services.auth().confirmationToken(user.getId());
        services.mail().sendConfirmation(user.getEmail(), user.getUsername(), token);
        Flash.info(ctx, "新的确认邮件已发送。");
        ctx.redirect(Routes.INDEX);
    }

    private void unconfirmed(Context ctx) {
        User user = CurrentUser.get(ctx);
        if (user == null || user.isConfirmed()) {
            ctx.redirect(Routes.INDEX);
            return;
        }
        View.render(ctx, "auth/unconfirmed.jte", "请确认你的账号");
    }

    /** 相当于原书的 @auth.before_app_request */
    public static void installUnconfirmedGuard(JavalinConfig config) {
        config.routes.beforeMatched(ctx -> {
            User user = CurrentUser.get(ctx);
            if (user == null || user.isConfirmed()) return;

            String path = ctx.path();
            // 白名单：认证相关的路径必须放过
            if (path.startsWith("/auth/") || path.equals("/favicon.ico")) return;
            // API 走自己的规则（第 14 章）
            if (path.startsWith("/api/")) return;

            ctx.redirect(Routes.UNCONFIRMED);
            ctx.skipRemainingHandlers();    // 别再执行后面的处理器
        });
    }

    // ---------- 修改密码 ----------

    private void changePasswordForm(Context ctx) {
        CurrentUser.require(ctx);
        View.render(ctx, "auth/change_password.jte", "修改密码", "form", FormView.empty());
    }

    private void changePassword(Context ctx) {
        User user = CurrentUser.require(ctx);
        var form = new ChangePasswordForm(
            ctx.formParam("oldPassword"),
            ctx.formParam("password"),
            ctx.formParam("password2"));

        var errors = new LinkedHashMap<String, List<String>>(Forms.validate(form));
        if (errors.isEmpty() && !user.checkPassword(form.oldPassword())) {
            errors.put("oldPassword", List.of("当前密码不正确"));
        }
        if (!errors.isEmpty()) {
            View.render(ctx, "auth/change_password.jte", "修改密码",
                        "form", new FormView(Map.of(), errors));
            return;
        }

        user.setPassword(form.password());

        // 改密码之后换一个会话 ID（和登录时同理）
        ctx.req().changeSessionId();

        Flash.success(ctx, "密码已更新。");
        ctx.redirect(Routes.INDEX);
    }

    // ---------- 重置密码 ----------

    private void resetRequestForm(Context ctx) {
        if (CurrentUser.isAuthenticated(ctx)) {
            ctx.redirect(Routes.INDEX);
            return;
        }
        View.render(ctx, "auth/reset_request.jte", "找回密码", "form", FormView.empty());
    }

    private void requestPasswordReset(Context ctx) {
        String email = nullToEmpty(ctx.formParam("email")).toLowerCase();

        services.users(ctx).findByEmail(email).ifPresent(user -> {
            String token = services.auth().resetToken(user.getId());
            services.mail().sendPasswordReset(user.getEmail(), user.getUsername(), token);
        });

        // 关键：无论邮箱存不存在，都给同一句提示。
        // 否则这个接口就变成了“查询某邮箱是否注册过”的工具。
        Flash.info(ctx, "如果这个邮箱已注册，重置链接已经发送过去了。");
        ctx.redirect(Routes.LOGIN);
    }

    private void resetForm(Context ctx) {
        View.render(ctx, "auth/reset_password.jte", "重置密码",
                    "form", FormView.empty(),
                    "token", ctx.pathParam("token"));
    }

    private void doPasswordReset(Context ctx) {
        var jwt = services.auth().tokens()
            .verify(ctx.pathParam("token"), AuthService.PURPOSE_RESET);
        if (jwt.isEmpty()) {
            Flash.danger(ctx, "重置链接无效或已过期。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        var form = new PasswordResetForm(ctx.formParam("password"), ctx.formParam("password2"));
        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            View.render(ctx, "auth/reset_password.jte", "重置密码",
                        "form", new FormView(Map.of(), errors),
                        "token", ctx.pathParam("token"));
            return;
        }

        User user = services.users(ctx).findById(Tokens.userIdOf(jwt.get())).orElse(null);
        if (user == null) {
            Flash.danger(ctx, "重置链接无效。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        user.setPassword(form.password());
        Flash.success(ctx, "密码已重置，请用新密码登录。");
        ctx.redirect(Routes.LOGIN);
    }

    // ---------- 修改邮箱 ----------

    private void emailChangeForm(Context ctx) {
        CurrentUser.require(ctx);
        View.render(ctx, "auth/change_email.jte", "修改邮箱", "form", FormView.empty());
    }

    private void requestEmailChange(Context ctx) {
        User user = CurrentUser.require(ctx);
        var form = new EmailChangeForm(ctx.formParam("email"), ctx.formParam("password"));

        var errors = new LinkedHashMap<String, List<String>>(Forms.validate(form));
        if (!errors.isEmpty()) {
            View.render(ctx, "auth/change_email.jte", "修改邮箱",
                        "form", new FormView(
                            Map.of("email", nullToEmpty(form.email())), errors));
            return;
        }

        String newEmail = form.email().toLowerCase();

        // 改邮箱是敏感操作，必须重新验证密码
        if (!user.checkPassword(form.password())) {
            Flash.danger(ctx, "密码不正确。");
            ctx.redirect(Routes.CHANGE_EMAIL);
            return;
        }
        if (services.users(ctx).emailExists(newEmail)) {
            Flash.danger(ctx, "这个邮箱已经被使用了。");
            ctx.redirect(Routes.CHANGE_EMAIL);
            return;
        }

        String token = services.auth().emailChangeToken(user.getId(), newEmail);
        services.mail().sendEmailChange(newEmail, user.getUsername(), token);

        Flash.info(ctx, "确认邮件已发送到新邮箱，请到那边完成确认。");
        ctx.redirect(Routes.INDEX);
    }

    private void confirmEmailChange(Context ctx) {
        var jwt = services.auth().tokens()
            .verify(ctx.pathParam("token"), AuthService.PURPOSE_EMAIL);
        if (jwt.isEmpty()) {
            Flash.danger(ctx, "链接无效或已过期。");
            ctx.redirect(Routes.INDEX);
            return;
        }

        User user = services.users(ctx).findById(Tokens.userIdOf(jwt.get())).orElse(null);
        String newEmail = jwt.get().getClaim("newEmail").asString();
        if (user == null || newEmail == null) {
            Flash.danger(ctx, "链接无效。");
            ctx.redirect(Routes.INDEX);
            return;
        }
        // 再查一次：令牌签发之后可能有人抢注了这个邮箱
        if (services.users(ctx).emailExists(newEmail)) {
            Flash.danger(ctx, "这个邮箱已经被使用了。");
            ctx.redirect(Routes.INDEX);
            return;
        }

        user.setEmail(newEmail);
        // 第 10 章：Gravatar 哈希是按邮箱算的，到时候要在这里重算
        Flash.success(ctx, "邮箱已更新。");
        ctx.redirect(Routes.INDEX);
    }

    // ---------- 小工具 ----------

    /**
     * 安全地取 next 参数。只接受“本站的绝对路径”：
     *   /edit-profile        ✓
     *   /post/5?page=2       ✓
     *   //evil.example/x     ✗ 协议相对 URL，会跳到外站
     *   /\evil.example/x     ✗ 某些浏览器会把 \ 当成 /
     *   https://evil/x       ✗ 显然不行
     *   javascript:alert(1)  ✗
     */
    private static String safeNext(Context ctx, String fallback) {
        String next = ctx.queryParam("next");
        if (next == null || next.isBlank()) return fallback;
        if (!next.startsWith("/")) return fallback;
        if (next.startsWith("//")) return fallback;
        if (next.length() > 1 && (next.charAt(1) == '\\' || next.charAt(1) == '/')) return fallback;
        return next;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
