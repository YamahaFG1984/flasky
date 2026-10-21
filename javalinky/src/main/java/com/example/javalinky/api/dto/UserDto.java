package com.example.javalinky.api.dto;

import com.example.javalinky.model.User;
import java.time.Instant;

/** 用户资源的公开表示。字段是手工挑的 —— 这就是重点。 */
public record UserDto(
    String url,
    long id,
    String username,
    String name,
    String location,
    String aboutMe,
    Instant memberSince,
    Instant lastSeen,
    String postsUrl,
    String timelineUrl,
    long postCount
) {
    public static UserDto from(User user, String baseUrl, long postCount) {
        return new UserDto(
            baseUrl + "/api/v1/users/" + user.getId(),
            user.getId(),
            user.getUsername(),
            user.getName(),
            user.getLocation(),
            user.getAboutMe(),
            user.getMemberSince(),
            user.getLastSeen(),
            baseUrl + "/api/v1/users/" + user.getId() + "/posts",
            baseUrl + "/api/v1/users/" + user.getId() + "/timeline",
            postCount);
        // 注意这里没有 email、没有 passwordHash、没有 role、没有 confirmed
    }
}
