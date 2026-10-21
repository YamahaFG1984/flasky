package com.example.javalinky.api;

import com.example.javalinky.db.Db;
import com.example.javalinky.db.UserRepository;
import com.example.javalinky.model.User;
import com.example.javalinky.service.AuthService;
import com.example.javalinky.util.Tokens;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.UnauthorizedResponse;

public class ApiAuth {

    public static final String CTX_USER = "apiUser";
    private static final String PURPOSE_API = "api";

    private final AuthService auth;

    public ApiAuth(AuthService auth) {
        this.auth = auth;
    }

    /** 装在 /api/* 上的认证过滤器 */
    public void authenticate(Context ctx) {
        // 换令牌那个接口自己处理认证，这里跳过
        if (ctx.path().endsWith("/tokens") && ctx.method().name().equals("POST")) return;

        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedResponse("需要在 Authorization 头里提供 Bearer 令牌");
        }

        var jwt = auth.tokens().verify(header.substring(7), PURPOSE_API)
            .orElseThrow(() -> new UnauthorizedResponse("令牌无效或已过期"));

        User user = new UserRepository(Db.em(ctx))
            .findById(Tokens.userIdOf(jwt))
            .orElseThrow(() -> new UnauthorizedResponse("令牌对应的用户不存在"));

        if (!user.isConfirmed()) {
            throw new ForbiddenResponse("账号尚未确认");
        }

        ctx.attribute(CTX_USER, user);
    }

    /** 当前 API 调用者 */
    public static User user(Context ctx) {
        User user = ctx.attribute(CTX_USER);
        if (user == null) throw new UnauthorizedResponse();
        return user;
    }

    /** POST /api/v1/tokens —— 用 HTTP Basic 换令牌 */
    public void issueToken(Context ctx) {
        var credentials = ctx.basicAuthCredentials();
        if (credentials == null) {
            throw new UnauthorizedResponse("需要 HTTP Basic 认证");
        }

        User user = new UserRepository(Db.em(ctx))
            .findByEmail(credentials.getUsername().toLowerCase())
            .filter(u -> u.checkPassword(credentials.getPassword()))
            .orElseThrow(() -> new UnauthorizedResponse("邮箱或密码错误"));

        if (!user.isConfirmed()) {
            throw new ForbiddenResponse("账号尚未确认");
        }

        long ttlSeconds = 3600;
        String token = auth.tokens().issue(PURPOSE_API, user.getId(),
            java.time.Duration.ofSeconds(ttlSeconds));

        ctx.json(new TokenResponse(token, "Bearer", ttlSeconds));
    }

    public record TokenResponse(String token, String tokenType, long expiresIn) {}
}
