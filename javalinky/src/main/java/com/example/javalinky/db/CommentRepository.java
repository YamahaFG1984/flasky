package com.example.javalinky.db;

import com.example.javalinky.model.Comment;
import com.example.javalinky.model.Post;
import com.example.javalinky.model.User;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CommentRepository {

    private final EntityManager em;

    public CommentRepository(EntityManager em) {
        this.em = em;
    }

    public Comment save(Comment comment) {
        em.persist(comment);
        return comment;
    }

    public Optional<Comment> findById(long id) {
        return Optional.ofNullable(em.find(Comment.class, id));
    }

    public long countByAuthor(User author) {
        return em.createQuery(
                "select count(c) from Comment c where c.author = :a", Long.class)
            .setParameter("a", author)
            .getSingleResult();
    }

    public long countByPost(Post post) {
        return em.createQuery(
                "select count(c) from Comment c where c.post = :p", Long.class)
            .setParameter("p", post)
            .getSingleResult();
    }

    /**
     * 某篇文章的评论，按时间正序（对话要顺着读）。
     * @param includeDisabled 协管员传 true，其他人传 false
     */
    public Page<Comment> findByPost(Post post, int page, int size, boolean includeDisabled) {
        String filter = includeDisabled ? "" : " and c.disabled = false";

        long total = em.createQuery(
                "select count(c) from Comment c where c.post = :p" + filter, Long.class)
            .setParameter("p", post)
            .getSingleResult();

        var items = em.createQuery("""
                select c from Comment c
                  join fetch c.author
                 where c.post = :p%s
                 order by c.timestamp asc
                """.formatted(filter), Comment.class)
            .setParameter("p", post)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();

        return Page.of(items, page, size, total);
    }

    public long countByPost(Post post, boolean includeDisabled) {
        String filter = includeDisabled ? "" : " and c.disabled = false";
        return em.createQuery(
                "select count(c) from Comment c where c.post = :p" + filter, Long.class)
            .setParameter("p", post)
            .getSingleResult();
    }

    /**
     * 批量查“每篇文章有多少条评论”。
     * 列表页千万别在循环里逐篇查 —— 那就是 N+1（第 16 章会用测试把它钉死）。
     */
    public Map<Long, Long> countCommentsByPost(List<Long> postIds) {
        if (postIds.isEmpty()) return Map.of();
        return em.createQuery("""
                select c.post.id, count(c)
                  from Comment c
                 where c.post.id in :ids
                   and c.disabled = false
                 group by c.post.id
                """, Object[].class)
            .setParameter("ids", postIds)
            .getResultStream()
            .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** 协管后台：全站评论，最新的在前 */
    public Page<Comment> findAllForModeration(int page, int size) {
        long total = em.createQuery("select count(c) from Comment c", Long.class)
            .getSingleResult();
        var items = em.createQuery("""
                select c from Comment c
                  join fetch c.author
                  join fetch c.post
                 order by c.timestamp desc
                """, Comment.class)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();
        return Page.of(items, page, size, total);
    }
}
