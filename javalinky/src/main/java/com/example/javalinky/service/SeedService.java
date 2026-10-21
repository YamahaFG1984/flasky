package com.example.javalinky.service;

import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Role;
import jakarta.persistence.EntityManager;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SeedService {

    /** 角色的唯一定义处。要改权限，改这里然后重新跑 seed-roles。 */
    private static final Map<String, Set<Permission>> ROLES = new LinkedHashMap<>();
    private static final String DEFAULT_ROLE = "普通用户";

    static {
        ROLES.put("普通用户", EnumSet.of(
            Permission.FOLLOW, Permission.COMMENT, Permission.WRITE));
        ROLES.put("协管员", EnumSet.of(
            Permission.FOLLOW, Permission.COMMENT, Permission.WRITE,
            Permission.MODERATE));
        ROLES.put("管理员", EnumSet.allOf(Permission.class));
    }

    private static EntityManager em;

    public static void use(EntityManager entityManager) {
        em = entityManager;
    }

    /**
     * 创建或更新全部角色。幂等：跑一次和跑一百次结果一样。
     * 相当于原书的 Role.insert_roles()。
     */
    public static void insertRoles() {
        ROLES.forEach((name, permissions) -> {
            Role role = em.createQuery("select r from Role r where r.name = :n", Role.class)
                .setParameter("n", name)
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    Role fresh = new Role(name, Set.of());
                    em.persist(fresh);
                    return fresh;
                });

            // 每次都按代码里的定义重设，这样调整权限后重跑即可生效
            role.setPermissions(permissions);
            role.setDefaultRole(name.equals(DEFAULT_ROLE));
        });
    }
}
