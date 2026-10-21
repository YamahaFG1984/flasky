package com.example.javalinky.web.page;

import com.example.javalinky.db.Page;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Post;
import com.example.javalinky.model.User;
import java.time.Instant;
import java.util.List;

public record PostListPage(Page<Item> page) {

    public record Item(
        long id,
        String bodyHtml,
        Instant timestamp,
        String authorUsername,
        String authorAvatarHash,
        long commentCount,
        boolean canEdit        // 当前访客能不能编辑这一篇
    ) {}

    public static PostListPage of(Page<Post> source, User viewer) {
        List<Item> items = source.items().stream()
            .map(post -> new Item(
                post.getId(),
                post.getBodyHtml(),
                post.getTimestamp(),
                post.getAuthor().getUsername(),
                post.getAuthor().avatarHash(),
                0,                              // 评论数，第 13 章填上
                canEdit(viewer, post)))
            .toList();

        return new PostListPage(
            Page.of(items, source.page(), source.size(), source.total()));
    }

    private static boolean canEdit(User viewer, Post post) {
        if (viewer == null) return false;
        return viewer.getId().equals(post.getAuthor().getId())
            || viewer.can(Permission.ADMIN);
    }
}
