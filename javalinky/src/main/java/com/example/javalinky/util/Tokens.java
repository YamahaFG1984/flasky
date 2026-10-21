package com.example.javalinky.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class Tokens {

    private static final String ISSUER = "javalinky";

    private final Algorithm algorithm;

    public Tokens(String secretKey) {
        this.algorithm = Algorithm.HMAC256(secretKey);
    }

    /** 签发一个有用途、有有效期、绑定某个用户的令牌 */
    public String issue(String purpose, long userId, Duration ttl) {
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(String.valueOf(userId))
            .withClaim("purpose", purpose)
            .withIssuedAt(Instant.now())
            .withExpiresAt(Instant.now().plus(ttl))
            .sign(algorithm);
    }

    /** 带额外数据的令牌（换邮箱需要把新邮箱带上） */
    public String issue(String purpose, long userId, Duration ttl,
                        String extraKey, String extraValue) {
        return JWT.create()
            .withIssuer(ISSUER)
            .withSubject(String.valueOf(userId))
            .withClaim("purpose", purpose)
            .withClaim(extraKey, extraValue)
            .withIssuedAt(Instant.now())
            .withExpiresAt(Instant.now().plus(ttl))
            .sign(algorithm);
    }

    /** 校验并解析。任何问题（签名错、过期、用途不符）都返回 empty */
    public Optional<DecodedJWT> verify(String token, String expectedPurpose) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            DecodedJWT jwt = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withClaim("purpose", expectedPurpose)
                .build()
                .verify(token);
            return Optional.of(jwt);
        } catch (JWTVerificationException e) {
            return Optional.empty();
        }
    }

    public static long userIdOf(DecodedJWT jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
