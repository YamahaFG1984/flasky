package com.example.javalinky.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * OAuth2 授权码流程的客户端（第 22 章）。
 *
 * 故意写成<b>和具体厂商无关</b>的：GitHub、Google、GitLab 的差别
 * 只是四个 URL 和返回的 JSON 字段名，流程完全一样。
 */
public final class OAuthClient {

    /** 一个服务商的配置。把差异都收在这里 */
    public record Provider(
        String name,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,
        String userInfoUrl,
        String scope,
        String idField,        // 用户信息 JSON 里「唯一 id」的字段名
        String emailField,
        String nameField
    ) {
        public boolean configured() {
            return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
        }

        /** GitHub 的参数。换别家只要改这几个 URL 和字段名 */
        public static Provider github(String clientId, String clientSecret) {
            return new Provider("github", clientId, clientSecret,
                "https://github.com/login/oauth/authorize",
                "https://github.com/login/oauth/access_token",
                "https://api.github.com/user",
                "read:user user:email",
                "id", "email", "login");
        }
    }

    /** 从服务商那里拿回来的身份 */
    public record ExternalUser(String providerId, String email, String name) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;

    public OAuthClient() {
        this(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)   // 换令牌时不该有重定向
            .build());
    }

    public OAuthClient(HttpClient http) {
        this.http = http;
    }

    // ---------- 第一步：把用户送去服务商 ----------

    public String authorizeUrl(Provider provider, String redirectUri,
                               String state, String codeChallenge) {
        return provider.authorizeUrl()
            + "?client_id=" + enc(provider.clientId())
            + "&redirect_uri=" + enc(redirectUri)
            + "&scope=" + enc(provider.scope())
            + "&state=" + enc(state)
            + "&code_challenge=" + enc(codeChallenge)
            + "&code_challenge_method=S256"
            + "&response_type=code";
    }

    /** state 和 PKCE 的 verifier 都是随机串，生成方式一样 */
    public static String randomUrlSafe() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** PKCE：challenge = BASE64URL(SHA256(verifier)) */
    public static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 居然没有 SHA-256", e);
        }
    }

    // ---------- 第二步：拿授权码去换令牌 ----------

    public Optional<String> exchangeCode(Provider provider, String redirectUri,
                                         String code, String codeVerifier) {
        String body = "client_id=" + enc(provider.clientId())
            + "&client_secret=" + enc(provider.clientSecret())
            + "&code=" + enc(code)
            + "&redirect_uri=" + enc(redirectUri)
            + "&code_verifier=" + enc(codeVerifier)
            + "&grant_type=authorization_code";

        try {
            var response = http.send(
                HttpRequest.newBuilder(URI.create(provider.tokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")    // GitHub 默认返回表单编码，要显式要 JSON
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) return Optional.empty();

            JsonNode json = JSON.readTree(response.body());
            JsonNode token = json.get("access_token");
            return token == null || token.asText().isBlank()
                ? Optional.empty()
                : Optional.of(token.asText());
        } catch (Exception e) {
            // 网络问题、超时、对方返回了不是 JSON 的东西 —— 一律当成登录失败。
            // 绝不能把异常细节透给用户，那里面可能带着 client_secret
            return Optional.empty();
        }
    }

    // ---------- 第三步：拿令牌去问「这是谁」 ----------

    public Optional<ExternalUser> fetchUser(Provider provider, String accessToken) {
        try {
            var response = http.send(
                HttpRequest.newBuilder(URI.create(provider.userInfoUrl()))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) return Optional.empty();

            JsonNode json = JSON.readTree(response.body());
            JsonNode id = json.get(provider.idField());
            if (id == null || id.asText().isBlank()) return Optional.empty();

            return Optional.of(new ExternalUser(
                id.asText(),
                text(json, provider.emailField()),
                text(json, provider.nameField())));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
