package com.example.javalinky.web.page;

import com.example.javalinky.model.User;
import com.example.javalinky.util.Gravatar;
import java.time.Instant;

public record UserProfilePage(
    long id,
    String username,
    String name,            // 保证非 null，没填就是空串
    String location,
    String aboutMe,
    String email,           // 只有管理员能看到，否则是空串
    Instant memberSince,
    Instant lastSeen,
    com.example.javalinky.util.Avatar avatar,
    long postCount,
    long commentCount,
    long followerCount,
    long followingCount,
    boolean isSelf,         // 看的是自己的主页吗
    boolean canFollow,      // 当前访客能不能关注这个人
    boolean isFollowing,    // 当前访客已经关注了吗
    boolean followsYou      // 这个人关注了当前访客吗
) {

    public static UserProfilePage of(User user, boolean viewerIsAdmin, boolean isSelf,
                                     long postCount, long commentCount,
                                     long followerCount, long followingCount,
                                     boolean canFollow, boolean isFollowing,
                                     boolean followsYou) {
        return new UserProfilePage(
            user.getId(),
            user.getUsername(),
            orEmpty(user.getName()),
            orEmpty(user.getLocation()),
            orEmpty(user.getAboutMe()),
            viewerIsAdmin ? user.getEmail() : "",
            user.getMemberSince(),
            user.getLastSeen(),
            com.example.javalinky.util.Avatar.of(user),
            postCount, commentCount, followerCount, followingCount,
            isSelf, canFollow, isFollowing, followsYou);
    }

    public boolean hasName()     { return !name.isEmpty(); }
    public boolean hasLocation() { return !location.isEmpty(); }
    public boolean hasAboutMe()  { return !aboutMe.isEmpty(); }
    public boolean hasEmail()    { return !email.isEmpty(); }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
