package com.example.javalinky.db;

import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import java.util.List;
import java.util.Optional;

public class UserRepository {

    private final EntityManager em;

    public UserRepository(EntityManager em) {
        this.em = em;
    }

    // ---------- 增 ----------
    public User save(User user) {
        em.persist(user);        // 瞬态 → 托管
        return user;
    }

    // ---------- 查 ----------
    /** 按主键查，相当于 Model.query.get(id) */
    public Optional<User> findById(long id) {
        return Optional.ofNullable(em.find(User.class, id));
    }

    /** 按条件查一个，相当于 filter_by(...).first() */
    public Optional<User> findByEmail(String email) {
        return em.createQuery(
                "select u from User u where lower(u.email) = lower(:email)", User.class)
            .setParameter("email", email)
            .getResultStream()      // 比 getSingleResult() 好：查不到不会抛异常
            .findFirst();
    }

    public Optional<User> findByUsername(String username) {
        return em.createQuery(
                "select u from User u where u.username = :name", User.class)
            .setParameter("name", username)
            .getResultStream()
            .findFirst();
    }

    /** 查全部，相当于 Model.query.all() */
    public List<User> findAll() {
        return em.createQuery("select u from User u order by u.username", User.class)
                 .getResultList();
    }

    /** 计数，相当于 Model.query.count() */
    public long count() {
        return em.createQuery("select count(u) from User u", Long.class)
                 .getSingleResult();
    }

    public boolean usernameExists(String username) {
        return em.createQuery(
                "select count(u) from User u where lower(u.username) = lower(:name)", Long.class)
            .setParameter("name", username)
            .getSingleResult() > 0;
    }

    public boolean emailExists(String email) {
        return em.createQuery(
                "select count(u) from User u where lower(u.email) = lower(:email)", Long.class)
            .setParameter("email", email)
            .getSingleResult() > 0;
    }

    // ---------- 改 ----------
    // 不需要写！托管对象改字段，提交事务时自动 UPDATE。

    // ---------- 删 ----------
    public void delete(User user) {
        em.remove(user);
    }
}
