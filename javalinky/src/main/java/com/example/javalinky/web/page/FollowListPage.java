package com.example.javalinky.web.page;

import com.example.javalinky.db.Page;
import com.example.javalinky.model.Follow;
import java.time.Instant;
import java.util.List;

public record FollowListPage(
    String title,              // "xxx 的粉丝" / "xxx 关注的人"
    String ownerUsername,
    String baseUrl,
    Page<Item> page
) {
    public record Item(String username, String name, String avatarHash, Instant since) {}

    /** showFollower=true 表示列"关注者"，false 表示列"被关注者" */
    public static FollowListPage of(String title, String ownerUsername, String baseUrl,
                                    Page<Follow> source, boolean showFollower) {
        List<Item> items = source.items().stream()
            .map(f -> new Item(
                (showFollower ? f.getFollower() : f.getFollowed()).getUsername(),
                orEmpty((showFollower ? f.getFollower() : f.getFollowed()).getName()),
                (showFollower ? f.getFollower() : f.getFollowed()).avatarHash(),
                f.getTimestamp()))
            .toList();
        return new FollowListPage(title, ownerUsername, baseUrl,
            Page.of(items, source.page(), source.size(), source.total()));
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
