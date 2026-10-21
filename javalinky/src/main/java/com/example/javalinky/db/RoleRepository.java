package com.example.javalinky.db;

import com.example.javalinky.model.Role;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;

public class RoleRepository {

    private final EntityManager em;

    public RoleRepository(EntityManager em) {
        this.em = em;
    }

    /** 新用户的默认角色 */
    public Role defaultRole() {
        return em.createQuery(
                "select r from Role r where r.defaultRole = true", Role.class)
            .getResultStream().findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "没有默认角色，请先执行 Cli seed-roles"));
    }

    public Optional<Role> findByName(String name) {
        return em.createQuery("select r from Role r where r.name = :n", Role.class)
            .setParameter("n", name)
            .getResultStream().findFirst();
    }

    public Optional<Role> findById(long id) {
        return Optional.ofNullable(em.find(Role.class, id));
    }

    /** 管理员后台的角色下拉框要用（第 10 章） */
    public List<Role> findAll() {
        return em.createQuery("select r from Role r order by r.name", Role.class)
            .getResultList();
    }
}
