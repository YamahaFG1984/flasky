package com.example.javalinky.db;

import com.example.javalinky.model.Follow;
import com.example.javalinky.model.FollowId;
import com.example.javalinky.model.User;
import jakarta.persistence.EntityManager;

import java.util.List;

public class FollowRepository {

    private final EntityManager em;

    public FollowRepository(EntityManager em) {
        this.em = em;
    }

    public boolean isFollowing(User follower, User followed) {
        if (follower == null || followed == null) return false;
        return em.find(Follow.class,
            new FollowId(follower.getId(), followed.getId())) != null;
    }

    public void follow(User follower, User followed) {
        if (isFollowing(follower, followed)) return;      // 幂等
        em.persist(new Follow(follower, followed));
    }

    public void unfollow(User follower, User followed) {
        Follow follow = em.find(Follow.class,
            new FollowId(follower.getId(), followed.getId()));
        if (follow != null) em.remove(follow);
    }

    /**
     * 关注数 / 粉丝数。
     * 注意要减去 1，因为每个人都关注了自己（见本章最后一节）。
     */
    public long countFollowing(User user) {
        return count("f.follower", user);
    }

    public long countFollowers(User user) {
        return count("f.followed", user);
    }

    private long count(String side, User user) {
        return em.createQuery(
                "select count(f) from Follow f where " + side + " = :u", Long.class)
            .setParameter("u", user)
            .getSingleResult();
    }

    /** 粉丝列表（分页）。join fetch 关注者本人，避免 N+1 */
    public Page<Follow> followersOf(User user, int page, int size) {
        long total = countFollowers(user);
        var items = em.createQuery("""
                select f from Follow f
                  join fetch f.follower
                 where f.followed = :u
                 order by f.timestamp desc
                """, Follow.class)
            .setParameter("u", user)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();
        return Page.of(items, page, size, total);
    }

    /** 关注列表（分页） */
    public Page<Follow> followingOf(User user, int page, int size) {
        long total = countFollowing(user);
        var items = em.createQuery("""
                select f from Follow f
                  join fetch f.followed
                 where f.follower = :u
                 order by f.timestamp desc
                """, Follow.class)
            .setParameter("u", user)
            .setFirstResult((page - 1) * size)
            .setMaxResults(size)
            .getResultList();
        return Page.of(items, page, size, total);
    }

    /** 对外展示的关注数：减掉“自己关注自己”那一条 */
    public long countFollowingVisible(User user) {
        return Math.max(0, countFollowing(user) - 1);
    }

    public long countFollowersVisible(User user) {
        return Math.max(0, countFollowers(user) - 1);
    }
}
