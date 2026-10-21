package com.example.javalinky.web.page;

import com.example.javalinky.db.Page;
import com.example.javalinky.model.Comment;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.User;
import java.time.Instant;
import java.util.List;

public record CommentListPage(long postId, Page<Item> page, boolean canModerate) {

    public record Item(
        long id,
        String bodyHtml,
        Instant timestamp,
        String authorUsername,
        com.example.javalinky.util.Avatar authorAvatar,
        boolean disabled
    ) {}

    public static CommentListPage of(Page<Comment> source, User viewer, long postId) {
        boolean canModerate = viewer != null && viewer.can(Permission.MODERATE);

        List<Item> items = source.items().stream()
            .map(c -> new Item(
                c.getId(),
                c.getBodyHtml(),
                c.getTimestamp(),
                c.getAuthor().getUsername(),
                com.example.javalinky.util.Avatar.of(c.getAuthor()),
                c.isDisabled()))
            .toList();

        return new CommentListPage(postId,
            Page.of(items, source.page(), source.size(), source.total()),
            canModerate);
    }
}
