package com.example.javalinky.api.dto;

import com.example.javalinky.model.Post;
import java.time.Instant;

public record PostDto(
    String url,
    long id,
    String body,          // Markdown 原文，客户端可能想自己渲染
    String bodyHtml,      // 已清洗的 HTML，方便直接显示
    Instant timestamp,
    String authorUrl,
    String authorUsername,
    String commentsUrl,
    long commentCount
) {
    public static PostDto from(Post post, String baseUrl, long commentCount) {
        String self = baseUrl + "/api/v1/posts/" + post.getId();
        return new PostDto(
            self,
            post.getId(),
            post.getBody(),
            post.getBodyHtml(),
            post.getTimestamp(),
            baseUrl + "/api/v1/users/" + post.getAuthor().getId(),
            post.getAuthor().getUsername(),
            self + "/comments",
            commentCount);
    }
}
