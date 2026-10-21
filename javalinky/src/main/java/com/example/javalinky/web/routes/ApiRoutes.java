package com.example.javalinky.web.routes;

import com.example.javalinky.api.*;
import com.example.javalinky.api.dto.*;
import com.example.javalinky.config.AppConfig;
import com.example.javalinky.db.Db;
import com.example.javalinky.db.Page;
import com.example.javalinky.model.*;
import com.example.javalinky.web.Services;
import io.javalin.config.JavalinConfig;
import io.javalin.http.*;

import static io.javalin.apibuilder.ApiBuilder.*;

public class ApiRoutes implements RouteModule {

    private final AppConfig cfg;
    private final Services services;
    private final ApiAuth apiAuth;

    public ApiRoutes(AppConfig cfg, Services services) {
        this.cfg = cfg;
        this.services = services;
        this.apiAuth = new ApiAuth(services.auth());
    }

    @Override
    public void register(JavalinConfig config) {

        // API 不用会话，所以也不需要 CSRF —— 在 Csrf 过滤器里已排除 /api/*
        config.routes.before("/api/*", apiAuth::authenticate);

        ApiErrors.install(config);

        config.routes.apiBuilder(() -> path("/api/v1", () -> {

            post("/tokens", apiAuth::issueToken);

            path("/posts", () -> {
                get("", this::listPosts);
                post("", this::createPost);
                path("/{id}", () -> {
                    get("", this::getPost);
                    patch("", this::updatePost);
                    get("/comments", this::listPostComments);
                    post("/comments", this::createComment);
                });
            });

            path("/users/{id}", () -> {
                get("", this::getUser);
                get("/posts", this::listUserPosts);
                get("/timeline", this::listUserTimeline);
            });

            get("/comments/{id}", this::getComment);
        }));
    }

    // ---------------- 文章 ----------------

    private void listPosts(Context ctx) {
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        var source = services.postRepo(ctx).findAll(page, cfg.postsPerPage());
        ctx.json(PagedResponse.of(source, p -> toDto(ctx, p),
                                  cfg.siteBaseUrl() + "/api/v1/posts"));
    }

    private void getPost(Context ctx) {
        ctx.json(toDto(ctx, loadPost(ctx)));
    }

    private void createPost(Context ctx) {
        User author = ApiAuth.user(ctx);
        if (!author.can(Permission.WRITE)) {
            throw new ForbiddenResponse("没有发布文章的权限");
        }

        // bodyValidator：反序列化 + 校验，失败自动 400
        var request = ctx.bodyValidator(PostRequest.class)
            .check(r -> r.body() != null && !r.body().isBlank(), "body 不能为空")
            .check(r -> r.body().length() <= 20000, "body 最长 20000 个字符")
            .get();

        Post post = services.postRepo(ctx).save(new Post(request.body(), author));
        Db.em(ctx).flush();   // 要拿到自增 id

        // 201 Created 必须带 Location 头，指向新建的资源
        ctx.status(HttpStatus.CREATED)
           .header(Header.LOCATION, cfg.siteBaseUrl() + "/api/v1/posts/" + post.getId())
           .json(toDto(ctx, post));
    }

    private void updatePost(Context ctx) {
        User me = ApiAuth.user(ctx);
        Post post = loadPost(ctx);

        // 和第 11 章一样：角色权限之外还要查归属
        boolean isAuthor = me.getId().equals(post.getAuthor().getId());
        if (!isAuthor && !me.can(Permission.ADMIN)) {
            throw new ForbiddenResponse("不能修改别人的文章");
        }

        var request = ctx.bodyValidator(PostRequest.class)
            .check(r -> r.body() != null && !r.body().isBlank(), "body 不能为空")
            .get();

        post.setBody(request.body());
        ctx.json(toDto(ctx, post));
    }

    // ---------------- 用户 ----------------

    private void getUser(Context ctx) {
        User user = loadUser(ctx);
        ctx.json(UserDto.from(user, cfg.siteBaseUrl(),
            services.postRepo(ctx).countByAuthor(user)));
    }

    private void listUserPosts(Context ctx) {
        User user = loadUser(ctx);
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        var source = services.postRepo(ctx).findByAuthor(user, page, cfg.postsPerPage());
        ctx.json(PagedResponse.of(source, p -> toDto(ctx, p),
            cfg.siteBaseUrl() + "/api/v1/users/" + user.getId() + "/posts"));
    }

    /** 这个人关注的人发的文章。只有本人能看自己的时间线 */
    private void listUserTimeline(Context ctx) {
        User user = loadUser(ctx);
        if (!ApiAuth.user(ctx).getId().equals(user.getId())) {
            throw new ForbiddenResponse("只能查看自己的时间线");
        }
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        var source = services.postRepo(ctx).findTimeline(user, page, cfg.postsPerPage());
        ctx.json(PagedResponse.of(source, p -> toDto(ctx, p),
            cfg.siteBaseUrl() + "/api/v1/users/" + user.getId() + "/timeline"));
    }

    // ---------------- 评论 ----------------

    private void listPostComments(Context ctx) {
        Post post = loadPost(ctx);
        int page = Page.clampPage(ctx.queryParamAsClass("page", Integer.class).getOrNull());
        var source = services.commentRepo(ctx)
            .findByPost(post, page, cfg.commentsPerPage(), false);
        ctx.json(PagedResponse.of(source, c -> CommentDto.from(c, cfg.siteBaseUrl()),
            cfg.siteBaseUrl() + "/api/v1/posts/" + post.getId() + "/comments"));
    }

    private void createComment(Context ctx) {
        User me = ApiAuth.user(ctx);
        if (!me.can(Permission.COMMENT)) {
            throw new ForbiddenResponse("没有评论的权限");
        }
        Post post = loadPost(ctx);

        var request = ctx.bodyValidator(CommentRequest.class)
            .check(r -> r.body() != null && !r.body().isBlank(), "body 不能为空")
            .check(r -> r.body().length() <= 2000, "body 最长 2000 个字符")
            .get();

        Comment comment = services.commentRepo(ctx)
            .save(new Comment(request.body(), post, me));
        Db.em(ctx).flush();

        ctx.status(HttpStatus.CREATED)
           .header(Header.LOCATION,
                   cfg.siteBaseUrl() + "/api/v1/comments/" + comment.getId())
           .json(CommentDto.from(comment, cfg.siteBaseUrl()));
    }

    private void getComment(Context ctx) {
        Comment comment = services.commentRepo(ctx)
            .findById(ctx.pathParamAsClass("id", Long.class).get())
            .orElseThrow(NotFoundResponse::new);
        // 被隐藏的评论对 API 也不可见（协管是网页端的事）
        if (comment.isDisabled()) throw new NotFoundResponse();
        ctx.json(CommentDto.from(comment, cfg.siteBaseUrl()));
    }

    // ---------------- 辅助 ----------------

    private Post loadPost(Context ctx) {
        return services.postRepo(ctx)
            .findById(ctx.pathParamAsClass("id", Long.class).get())
            .orElseThrow(NotFoundResponse::new);
    }

    private User loadUser(Context ctx) {
        return services.users(ctx)
            .findById(ctx.pathParamAsClass("id", Long.class).get())
            .orElseThrow(NotFoundResponse::new);
    }

    private PostDto toDto(Context ctx, Post post) {
        return PostDto.from(post, cfg.siteBaseUrl(),
            services.commentRepo(ctx).countByPost(post, false));
    }

    /** 请求体的形状。注意它和 PostDto 是两个不同的类 —— 见下面说明 */
    public record PostRequest(String body) {}

    public record CommentRequest(String body) {}
}
