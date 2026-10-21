package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import com.example.javalinky.service.AuthService;
import com.example.javalinky.model.OAuthIdentity;
import com.example.javalinky.service.OAuthClient;
import com.example.javalinky.service.TwoFactorService;
import com.example.javalinky.util.Tokens;
import com.example.javalinky.util.Totp;

import java.time.Instant;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.*;
import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;

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

            // 第 22 章：两步验证
            // 注意这里的路径是<b>相对</b> path("/auth") 的 ——
            // 写成 Routes.TWO_FACTOR（"/auth/2fa"）会变成 /auth/auth/2fa
            // 第 22 章：第三方登录
            get("/oauth/{provider}",          this::oauthStart);
            get("/oauth/{provider}/callback", this::oauthCallback);

            get("/2fa",  this::twoFactorChallengeForm);   // 登录中途，还没登录成功
            post("/2fa", this::twoFactorChallenge);
            get("/2fa/setup",    this::twoFactorSetupForm, Access.AUTHENTICATED);
            post("/2fa/enable",  this::twoFactorEnable,    Access.AUTHENTICATED);
            post("/2fa/disable", this::twoFactorDisable,   Access.AUTHENTICATED);

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

        // 第 19 章：按来源 IP 限流，挡住在线撞库。
        // 放在校验之后、查库之前 —— 格式都不对的请求不值得占一个令牌，
        // 但也绝不能等查完库才限流，那样每次尝试都付出一次 bcrypt 的代价。
        String limitKey = ctx.ip();
        if (!services.loginLimiter().tryAcquire(limitKey)) {
            ctx.status(429);
            Flash.danger(ctx, "登录尝试过于频繁，请稍后再试。");
            renderLoginWithErrors(ctx, form, Map.of());
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

        // 登录成功，把这个 IP 的失败记录清掉
        services.loginLimiter().reset(limitKey);

        // 第 22 章：开了两步验证的话，密码只是第一步。
        // 关键：这里<b>还没有</b>调 CurrentUser.login —— 会话里只放一个「待验证」的标记，
        // 它不是登录态，拿着它访问任何受保护页面都会被挡住。
        if (user.isTotpEnabled()) {
            ctx.sessionAttribute(PENDING_2FA_USER, user.getId());
            ctx.sessionAttribute(PENDING_2FA_SINCE, System.currentTimeMillis());
            ctx.sessionAttribute(PENDING_2FA_NEXT, safeNext(ctx, Routes.INDEX));
            ctx.redirect(Routes.TWO_FACTOR);
            return;
        }

        CurrentUser.login(ctx, user);
        Flash.success(ctx, "欢迎回来，" + user.getUsername() + "！");
        ctx.redirect(safeNext(ctx, Routes.INDEX));
    }

    // ---------- 第 22 章：第三方登录 ----------

    private static final String OAUTH_STATE    = "oauthState";
    private static final String OAUTH_VERIFIER = "oauthVerifier";
    private static final String OAUTH_NEXT     = "oauthNext";

    private void oauthStart(Context ctx) {
        var provider = providerOrNull(ctx.pathParam("provider"));
        if (provider == null) throw new NotFoundResponse("没有配置这个登录方式");

        // state 防 CSRF：回调时必须带回同一个值，证明这次回调是我们发起的。
        // 少了它，攻击者可以把自己的授权码塞给受害者，把受害者的账号绑到自己头上。
        String state = OAuthClient.randomUrlSafe();
        // PKCE 的 verifier 防「授权码被截获」：换令牌时要出示它
        String verifier = OAuthClient.randomUrlSafe();

        ctx.sessionAttribute(OAUTH_STATE, state);
        ctx.sessionAttribute(OAUTH_VERIFIER, verifier);
        ctx.sessionAttribute(OAUTH_NEXT, safeNext(ctx, Routes.INDEX));

        ctx.redirect(services.oauth().authorizeUrl(
            provider, redirectUri(ctx, provider.name()),
            state, OAuthClient.codeChallenge(verifier)));
    }

    private void oauthCallback(Context ctx) {
        var provider = providerOrNull(ctx.pathParam("provider"));
        if (provider == null) throw new NotFoundResponse("没有配置这个登录方式");

        String expectedState = ctx.sessionAttribute(OAUTH_STATE);
        String verifier      = ctx.sessionAttribute(OAUTH_VERIFIER);
        String next          = ctx.sessionAttribute(OAUTH_NEXT);

        // 会话里的一次性值用完就清，别留着给下一次复用
        ctx.sessionAttribute(OAUTH_STATE, null);
        ctx.sessionAttribute(OAUTH_VERIFIER, null);
        ctx.sessionAttribute(OAUTH_NEXT, null);

        String state = ctx.queryParam("state");
        if (expectedState == null || state == null || !expectedState.equals(state)) {
            Flash.danger(ctx, "登录请求已失效，请重试。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        String code = ctx.queryParam("code");
        if (code == null || code.isBlank()) {
            // 用户在服务商页面点了「取消」，或者对方报错
            Flash.info(ctx, "已取消第三方登录。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        var token = services.oauth().exchangeCode(
            provider, redirectUri(ctx, provider.name()), code, verifier);
        var external = token.flatMap(t -> services.oauth().fetchUser(provider, t));
        if (external.isEmpty()) {
            Flash.danger(ctx, "第三方登录失败，请稍后再试或用密码登录。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        User user = linkOrCreate(ctx, provider.name(), external.get());
        if (user == null) {
            ctx.redirect(Routes.LOGIN);
            return;
        }

        CurrentUser.login(ctx, user);
        Flash.success(ctx, "欢迎回来，" + user.getUsername() + "！");
        ctx.redirect(next == null ? Routes.INDEX : next);
    }

    /**
     * 找到或创建本地账号。
     * 顺序很重要：<b>先按服务商 id 找</b>，找不到才考虑按邮箱关联。
     */
    private User linkOrCreate(Context ctx, String providerName,
                              OAuthClient.ExternalUser external) {
        var em = Db.em(ctx);

        var existing = em.createQuery("""
                select i.user from OAuthIdentity i
                 where i.provider = :p and i.providerUserId = :id
                """, User.class)
            .setParameter("p", providerName)
            .setParameter("id", external.providerId())
            .getResultStream().findFirst();
        if (existing.isPresent()) return existing.get();

        String email = external.email() == null ? null : external.email().toLowerCase();
        if (email == null || email.isBlank()) {
            // 很多服务商默认不给邮箱（GitHub 的邮箱可以设为私密）。
            // 没有邮箱就没法安全地关联已有账号 —— 让用户先用密码登录再去绑定。
            Flash.danger(ctx, "没能从第三方拿到你的邮箱，请先用密码登录后在设置里绑定。");
            return null;
        }

        var byEmail = services.users(ctx).findByEmail(email);
        if (byEmail.isPresent()) {
            User user = byEmail.get();
            // 这里是账号接管风险最高的一步，见正文的说明
            if (!user.isConfirmed()) {
                Flash.danger(ctx, "这个邮箱对应的账号还没有验证，请先完成邮箱验证。");
                return null;
            }
            em.persist(new OAuthIdentity(user, providerName, external.providerId()));
            return user;
        }

        // 全新用户：建一个账号。邮箱由服务商验证过，所以直接标记为已确认
        String username = uniqueUsername(ctx, external.name(), email);
        User user = new User(email, username);
        user.setPassword(OAuthClient.randomUrlSafe());   // 随机密码 = 只能用第三方登录
        user.setConfirmed(true);
        user.setRole(services.roles(ctx).defaultRole());
        services.users(ctx).save(user);
        em.flush();
        em.persist(new OAuthIdentity(user, providerName, external.providerId()));
        return user;
    }

    private String uniqueUsername(Context ctx, String preferred, String email) {
        String base = preferred == null || preferred.isBlank()
            ? email.substring(0, email.indexOf('@'))
            : preferred;
        base = base.replaceAll("[^A-Za-z0-9_.]", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) base = "u" + base;
        if (base.length() > 48) base = base.substring(0, 48);

        var users = services.users(ctx);
        String candidate = base;
        for (int i = 2; users.usernameExists(candidate); i++) {
            candidate = base + i;
        }
        return candidate;
    }

    private OAuthClient.Provider providerOrNull(String name) {
        if (!"github".equals(name)) return null;
        var p = new OAuthClient.Provider("github",
            cfg.githubClientId(), cfg.githubClientSecret(),
            cfg.githubAuthorizeUrl(), cfg.githubTokenUrl(), cfg.githubUserInfoUrl(),
            "read:user user:email", "id", "email", "login");
        return p.configured() ? p : null;
    }

    /**
     * 回调地址。必须和在服务商后台登记的完全一致，而且
     * <b>要用配置里的 siteBaseUrl，不能用请求里的 Host 头</b> ——
     * Host 是客户端可控的，拿它拼地址等于把授权码送给攻击者。
     */
    private String redirectUri(Context ctx, String providerName) {
        return cfg.siteBaseUrl() + "/auth/oauth/" + providerName + "/callback";
    }

    // ---------- 第 22 章：两步验证 ----------

    private static final String PENDING_2FA_USER  = "pending2faUserId";
    private static final String PENDING_2FA_SINCE = "pending2faSince";
    private static final String PENDING_2FA_NEXT  = "pending2faNext";

    /** 待验证状态的有效期。超时就要重新输密码 */
    private static final long PENDING_2FA_TTL_MILLIS = 5 * 60 * 1000L;

    private void twoFactorChallengeForm(Context ctx) {
        if (pendingUser(ctx) == null) {
            ctx.redirect(Routes.LOGIN);
            return;
        }
        View.render(ctx, "auth/two_factor.jte", "两步验证", "form", FormView.empty());
    }

    private void twoFactorChallenge(Context ctx) {
        User user = pendingUser(ctx);
        if (user == null) {
            Flash.danger(ctx, "验证超时，请重新登录。");
            ctx.redirect(Routes.LOGIN);
            return;
        }

        // 验证码一样要限流：六位数只有一百万种可能，不限流几分钟就能爆破完
        if (!services.loginLimiter().tryAcquire("2fa:" + ctx.ip())) {
            ctx.status(429);
            Flash.danger(ctx, "尝试过于频繁，请稍后再试。");
            View.render(ctx, "auth/two_factor.jte", "两步验证", "form", FormView.empty());
            return;
        }

        var result = services.twoFactor(ctx).verify(user, ctx.formParam("code"), Instant.now());
        if (result != TwoFactorService.Result.OK) {
            Flash.danger(ctx, result == TwoFactorService.Result.REPLAYED
                ? "这个验证码已经用过了，请等下一个。"
                : "验证码不对。");
            View.render(ctx, "auth/two_factor.jte", "两步验证", "form", FormView.empty());
            return;
        }

        String next = ctx.sessionAttribute(PENDING_2FA_NEXT);
        clearPending(ctx);
        CurrentUser.login(ctx, user);
        Flash.success(ctx, "欢迎回来，" + user.getUsername() + "！");
        ctx.redirect(next == null ? Routes.INDEX : next);
    }

    private void twoFactorSetupForm(Context ctx) {
        User me = CurrentUser.require(ctx);

        if (me.isTotpEnabled()) {
            View.render(ctx, "auth/two_factor_setup.jte", "两步验证",
                        "enabled", true, "secret", null, "uri", null,
                        "remaining", services.twoFactor(ctx).unusedRecoveryCodeCount(me),
                        "codes", List.<String>of());
            return;
        }

        // 密钥先放会话里，<b>验证通过之后才写进数据库</b> ——
        // 否则用户扫到一半走了，账号就带着一个没人知道的密钥，下次登录直接被锁死
        String secret = ctx.sessionAttribute(SETUP_SECRET);
        if (secret == null) {
            secret = Totp.newSecret();
            ctx.sessionAttribute(SETUP_SECRET, secret);
        }

        View.render(ctx, "auth/two_factor_setup.jte", "两步验证",
                    "enabled", false,
                    "secret", secret,
                    "uri", Totp.provisioningUri("Javalinky", me.getEmail(), secret),
                    "remaining", 0L,
                    "codes", List.<String>of());
    }

    private void twoFactorEnable(Context ctx) {
        User me = CurrentUser.require(ctx);
        String secret = ctx.sessionAttribute(SETUP_SECRET);

        if (me.isTotpEnabled() || secret == null) {
            ctx.redirect(Routes.TWO_FACTOR_SETUP);
            return;
        }

        // 要求用户先输一次码，证明他的认证器确实配好了。
        // 跳过这一步的话，配错了也不知道，等到下次登录才发现 —— 那时已经进不来了。
        if (Totp.verify(secret, ctx.formParam("code"), Instant.now().getEpochSecond()) == -1) {
            Flash.danger(ctx, "验证码不对，请确认手机上的时间是自动同步的。");
            ctx.redirect(Routes.TWO_FACTOR_SETUP);
            return;
        }

        me.setTotpSecret(secret);
        me.setTotpEnabled(true);
        me.setTotpLastSlot(null);
        ctx.sessionAttribute(SETUP_SECRET, null);

        var codes = services.twoFactor(ctx).regenerateRecoveryCodes(me);
        Flash.success(ctx, "两步验证已开启。");

        // 恢复码只在这一次显示 —— 存的是哈希，之后谁也拿不回来
        View.render(ctx, "auth/two_factor_setup.jte", "两步验证",
                    "enabled", true, "secret", null, "uri", null,
                    "remaining", (long) codes.size(), "codes", codes);
    }

    private void twoFactorDisable(Context ctx) {
        User me = CurrentUser.require(ctx);

        // 关掉它是降低安全等级的操作，必须重新验证密码
        if (!me.checkPassword(ctx.formParam("password"))) {
            Flash.danger(ctx, "密码不对。");
            ctx.redirect(Routes.TWO_FACTOR_SETUP);
            return;
        }

        services.twoFactor(ctx).disable(me);
        Flash.info(ctx, "两步验证已关闭。");
        ctx.redirect(Routes.TWO_FACTOR_SETUP);
    }

    private static final String SETUP_SECRET = "totpSetupSecret";

    /** 取「密码已通过、等待第二步」的那个用户。超时或不存在返回 null */
    private User pendingUser(Context ctx) {
        Long userId = ctx.sessionAttribute(PENDING_2FA_USER);
        Long since  = ctx.sessionAttribute(PENDING_2FA_SINCE);
        if (userId == null || since == null) return null;

        if (System.currentTimeMillis() - since > PENDING_2FA_TTL_MILLIS) {
            clearPending(ctx);
            return null;
        }
        return services.users(ctx).findById(userId).orElse(null);
    }

    private void clearPending(Context ctx) {
        ctx.sessionAttribute(PENDING_2FA_USER, null);
        ctx.sessionAttribute(PENDING_2FA_SINCE, null);
        ctx.sessionAttribute(PENDING_2FA_NEXT, null);
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

        // 3. 创建用户并分配角色：配置里的管理员邮箱直接拿管理员角色
        var roles = services.roles(ctx);
        Role role = email.equalsIgnoreCase(cfg.adminEmail())
            ? roles.findByName("管理员").orElseGet(roles::defaultRole)
            : roles.defaultRole();

        var user = new User(email, form.username());
        user.setPassword(form.password());
        user.setRole(role);
        users.save(user);

        // 4. flush 拿到主键：后面发邮件要用 id，自关注也要用
        Db.em(ctx).flush();
        services.follows(ctx).follow(user, user);    // ← 自己关注自己，时间线里才有自己的文章

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
        user.setAvatarHash(null);      // Gravatar 哈希是按邮箱算的，要重算
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
