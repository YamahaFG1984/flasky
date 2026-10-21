package com.example.javalinky.api.dto;

import com.example.javalinky.model.Comment;
import java.time.Instant;

public record CommentDto(
    String url,
    long id,
    String body,
    String bodyHtml,
    Instant timestamp,
    String postUrl,
    String authorUrl,
    String authorUsername
) {
    public static CommentDto from(Comment comment, String baseUrl) {
        return new CommentDto(
            baseUrl + "/api/v1/comments/" + comment.getId(),
            comment.getId(),
            comment.getBody(),
            comment.getBodyHtml(),
            comment.getTimestamp(),
            baseUrl + "/api/v1/posts/" + comment.getPost().getId(),
            baseUrl + "/api/v1/users/" + comment.getAuthor().getId(),
            comment.getAuthor().getUsername());
    }
}
