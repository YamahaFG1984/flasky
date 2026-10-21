package com.example.javalinky.web.routes;

import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Page;
import com.example.javalinky.model.Post;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import com.example.javalinky.web.*;
import com.example.javalinky.web.form.AdminEditProfileForm;
import com.example.javalinky.web.form.EditProfileForm;
import com.example.javalinky.web.form.PostForm;
import com.example.javalinky.web.page.FollowListPage;
import com.example.javalinky.web.page.PostListPage;
import com.example.javalinky.web.page.UserProfilePage;
import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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
            get("/user/{username}", this::userProfile);
            get("/user/{username}/followers", this::followers);
            get("/user/{username}/following", this::following);
            get("/post/{id}", this::showPost);

            // 首页的两个标签页（用 POST + CSRF，因为它改变了服务端认知的状态）
            post("/show-all",      this::showAll,      Access.AUTHENTICATED);
            post("/show-followed", this::showFollowed, Access.AUTHENTICATED);

            // 必须登录
            get("/edit-profile",  this::editProfileForm, Access.AUTHENTICATED);
            post("/edit-profile", this::editProfile,     Access.AUTHENTICATED);

            // 需要 FOLLOW 权限
            post("/follow/{username}",   this::follow,   Permission.FOLLOW);
            post("/unfollow/{username}", this::unfollow, Permission.FOLLOW);

            // 需要 WRITE 权限
            post("/post",      this::createPost,   Permission.WRITE);
            get("/edit/{id}",  this::editPostForm, Permission.WRITE);
            post("/edit/{id}", this::editPost,     Permission.WRITE);

            // 管理员专属：整个前缀挂一个角色
            path("/admin", List.of(Permission.ADMIN), () -> {
                get("/edit-profile/{id}",  this::adminEditProfileForm);
                post("/edit-profile/{id}", this::adminEditProfile);
            });
        });
    }

    // ---------- 首页 ----------

    private static final String COOKIE_SHOW_FOLLOWED = "show_followed";

    private void index(Context ctx) {
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        User me = CurrentUser.get(ctx);

        // 只有登录用户才可能选“只看关注”
        boolean showFollowed = me != null
            && "1".equals(ctx.cookie(COOKIE_SHOW_FOLLOWED));

        var repo = services.postRepo(ctx);
        Page<Post> posts = showFollowed
            ? repo.findTimeline(me, page, cfg.postsPerPage())
            : repo.findAll(page, cfg.postsPerPage());

        View.render(ctx, "index.jte", "首页",
                    "form", FormView.empty(),
                    "posts", PostListPage.of(posts, me),
                    "showFollowed", showFollowed);
    }

    /** 切换到“全部” */
    private void showAll(Context ctx) {
        CurrentUser.require(ctx);
        ctx.cookie(COOKIE_SHOW_FOLLOWED, "", 0);     // maxAge=0 就是删除
        ctx.redirect(Routes.INDEX);
    }

    /** 切换到“只看关注” */
    private void showFollowed(Context ctx) {
        CurrentUser.require(ctx);
        ctx.cookie(COOKIE_SHOW_FOLLOWED, "1", 30 * 24 * 60 * 60);   // 30 天
        ctx.redirect(Routes.INDEX);
    }

    // ---------- 文章 ----------

    private void createPost(Context ctx) {
        User author = CurrentUser.require(ctx);
        var form = new PostForm(ctx.formParam("body"));

        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            Flash.danger(ctx, errors.values().iterator().next().get(0));
            ctx.redirect(Routes.INDEX);
            return;
        }

        services.postRepo(ctx).save(new Post(form.body(), author));
        Flash.success(ctx, "文章已发布。");
        ctx.redirect(Routes.INDEX);     // Post/Redirect/Get
    }

    /**
     * 单篇文章页。URL: /post/{id}
     * 方法名不能叫 post —— 会盖住静态导入的 ApiBuilder.post(...)，
     * 编译器会报 "method post ... cannot be applied to given types"。
     */
    private void showPost(Context ctx) {
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Post post = services.postRepo(ctx).findById(id)
            .orElseThrow(() -> new NotFoundResponse("文章不存在"));

        // 第 13 章会在这里加上评论列表
        View.render(ctx, "post.jte", "文章",
                    "post", PostListPage.of(
                        Page.of(List.of(post), 1, 1, 1), CurrentUser.get(ctx)));
    }

    private void editPostForm(Context ctx) {
        Post post = loadEditablePost(ctx);
        View.render(ctx, "edit_post.jte", "编辑文章",
                    "form", new FormView(Map.of("body", post.getBody()), Map.of()),
                    "postId", post.getId());
    }

    private void editPost(Context ctx) {
        Post post = loadEditablePost(ctx);
        var form = new PostForm(ctx.formParam("body"));

        var errors = Forms.validate(form);
        if (!errors.isEmpty()) {
            View.render(ctx, "edit_post.jte", "编辑文章",
                        "form", new FormView(Map.of("body", orEmpty(form.body())), errors),
                        "postId", post.getId());
            return;
        }

        post.setBody(form.body());     // @PreUpdate 会自动重新渲染 bodyHtml
        Flash.success(ctx, "文章已更新。");
        ctx.redirect(Routes.post(post.getId()));
    }

    /**
     * 取出文章并检查“当前用户能不能编辑它”。
     * 注意：路由上的 Permission.WRITE 只保证“这个人有发文章的权限”，
     * 不保证“这篇文章是他的” —— 后者必须在这里查。
     */
    private Post loadEditablePost(Context ctx) {
        User user = CurrentUser.require(ctx);
        long id = ctx.pathParamAsClass("id", Long.class).get();
        Post post = services.postRepo(ctx).findById(id)
            .orElseThrow(() -> new NotFoundResponse("文章不存在"));

        boolean isAuthor = user.getId().equals(post.getAuthor().getId());
        if (!isAuthor && !user.can(Permission.ADMIN)) {
            throw new ForbiddenResponse("你不能编辑别人的文章");
        }
        return post;
    }

    // ---------- 用户主页 ----------

    private void userProfile(Context ctx) {
        User user = loadUserByUsername(ctx);
        User viewer = CurrentUser.get(ctx);
        var postRepo = services.postRepo(ctx);
        var follows = services.follows(ctx);

        int pageNo = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        var posts = postRepo.findByAuthor(user, pageNo, cfg.postsPerPage());

        var page = UserProfilePage.of(
            user,
            viewer != null && viewer.isAdministrator(),
            viewer != null && viewer.getId().equals(user.getId()),
            postRepo.countByAuthor(user), 0,   // 评论数第 13 章填上
            follows.countFollowersVisible(user),
            follows.countFollowingVisible(user),
            viewer != null && viewer.can(Permission.FOLLOW)
                && !viewer.getId().equals(user.getId()),
            follows.isFollowing(viewer, user),
            follows.isFollowing(user, viewer));

        View.render(ctx, "user.jte", user.getUsername(),
                    "page", page,
                    "posts", PostListPage.of(posts, viewer));
    }

    // ---------- 关注 ----------

    private void follow(Context ctx) {
        User me = CurrentUser.require(ctx);
        User target = loadUserByUsername(ctx);

        if (me.getId().equals(target.getId())) {
            Flash.warning(ctx, "你不能关注自己。");
            ctx.redirect(Routes.user(target.getUsername()));
            return;
        }

        var follows = services.follows(ctx);
        if (follows.isFollowing(me, target)) {
            Flash.info(ctx, "你已经关注了 " + target.getUsername() + "。");
        } else {
            follows.follow(me, target);
            Flash.success(ctx, "已关注 " + target.getUsername() + "。");
        }
        ctx.redirect(Routes.user(target.getUsername()));
    }

    private void unfollow(Context ctx) {
        User me = CurrentUser.require(ctx);
        User target = loadUserByUsername(ctx);

        services.follows(ctx).unfollow(me, target);
        Flash.info(ctx, "已取消关注 " + target.getUsername() + "。");
        ctx.redirect(Routes.user(target.getUsername()));
    }

    private void followers(Context ctx) {
        User user = loadUserByUsername(ctx);
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());

        var source = services.follows(ctx)
            .followersOf(user, page, cfg.followersPerPage());

        View.render(ctx, "follow_list.jte", user.getUsername() + " 的粉丝",
            "page", FollowListPage.of(
                user.getUsername() + " 的粉丝",
                user.getUsername(),
                Routes.followers(user.getUsername()),
                source, true));
    }

    private void following(Context ctx) {
        User user = loadUserByUsername(ctx);
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());

        var source = services.follows(ctx)
            .followingOf(user, page, cfg.followersPerPage());

        View.render(ctx, "follow_list.jte", user.getUsername() + " 关注的人",
            "page", FollowListPage.of(
                user.getUsername() + " 关注的人",
                user.getUsername(),
                Routes.following(user.getUsername()),
                source, false));
    }

    private User loadUserByUsername(Context ctx) {
        return services.users(ctx).findByUsername(ctx.pathParam("username"))
            .orElseThrow(() -> new NotFoundResponse("没有这个用户"));
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
