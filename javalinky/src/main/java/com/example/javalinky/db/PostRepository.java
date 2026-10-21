package com.example.javalinky.db;

import com.example.javalinky.model.Post;
import com.example.javalinky.model.User;
import jakarta.persistence.EntityManager;
import java.util.Optional;

public class PostRepository {

    private final EntityManager em;

    public PostRepository(EntityManager em) {
        this.em = em;
    }

    public Post save(Post post) {
        em.persist(post);
        return post;
    }

    public Optional<Post> findById(long id) {
        return Optional.ofNullable(em.find(Post.class, id));
    }

    public long countByAuthor(User author) {
        return em.createQuery(
                "select count(p) from Post p where p.author = :a", Long.class)
            .setParameter("a", author)
            .getSingleResult();
    }

    /** 全部文章，按时间倒序分页。join fetch 作者，避免列表页 N+1 */
    public Page<Post> findAll(int page, int size) {
        long total = em.createQuery("select count(p) from Post p", Long.class)
            .getSingleResult();

        var items = em.createQuery("""
                select p from Post p
                  join fetch p.author a
                 order by p.timestamp desc
                """, Post.class)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();

        return Page.of(items, page, size, total);
    }

    public Page<Post> findByAuthor(User author, int page, int size) {
        long total = countByAuthor(author);
        var items = em.createQuery("""
                select p from Post p
                  join fetch p.author a
                 where p.author = :author
                 order by p.timestamp desc
                """, Post.class)
            .setParameter("author", author)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();
        return Page.of(items, page, size, total);
    }

    /**
     * 查出某用户关注的人发的全部文章（他的“时间线”）。
     *
     * 对应的 SQL 大致是：
     *   select p.* from posts p
     *     join follows f on f.followed_id = p.author_id
     *    where f.follower_id = ?
     *    order by p.timestamp desc
     *    limit ? offset ?
     */
    public Page<Post> findTimeline(User user, int page, int size) {
        long total = em.createQuery("""
                select count(p)
                  from Post p
                  join Follow f on f.followed = p.author
                 where f.follower = :me
                """, Long.class)
            .setParameter("me", user)
            .getSingleResult();

        var items = em.createQuery("""
                select p
                  from Post p
                  join fetch p.author
                  join Follow f on f.followed = p.author
                 where f.follower = :me
                 order by p.timestamp desc
                """, Post.class)
            .setParameter("me", user)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();

        return Page.of(items, page, size, total);
    }
}
