package com.example.javalinky.web.page;

import com.example.javalinky.db.Page;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Post;
import com.example.javalinky.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PostListPage(Page<Item> page) {

    public record Item(
        long id,
        String bodyHtml,
        Instant timestamp,
        String authorUsername,
        com.example.javalinky.util.Avatar authorAvatar,
        long commentCount,
        boolean canEdit        // 当前访客能不能编辑这一篇
    ) {}

    public static PostListPage of(Page<Post> source, User viewer) {
        return of(source, viewer, Map.of());
    }

    /**
     * @param commentCounts 文章 id → 评论数。一条聚合查询批量查出来，
     *                      不要在这里逐篇 post.getComments().size() —— 那是 N+1。
     */
    public static PostListPage of(Page<Post> source, User viewer,
                                  Map<Long, Long> commentCounts) {
        List<Item> items = source.items().stream()
            .map(post -> new Item(
                post.getId(),
                post.getBodyHtml(),
                post.getTimestamp(),
                post.getAuthor().getUsername(),
                com.example.javalinky.util.Avatar.of(post.getAuthor()),
                commentCounts.getOrDefault(post.getId(), 0L),
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
