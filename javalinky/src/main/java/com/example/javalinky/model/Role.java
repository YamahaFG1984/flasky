package com.example.javalinky.model;

import jakarta.persistence.*;
import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, unique = true, nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultRole;

    /** 权限位。数据库里是一个 int，代码里尽量别直接碰它 */
    @Column(nullable = false)
    private int permissions;

    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private java.util.List<User> users = new java.util.ArrayList<>();

    protected Role() {}

    public Role(String name, Set<Permission> permissions) {
        this.name = name;
        this.permissions = Permission.toBits(permissions);
    }

    // ---------- 权限操作 ----------

    public EnumSet<Permission> permissions() {
        return Permission.fromBits(permissions);
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = Permission.toBits(permissions);
    }

    public boolean has(Permission permission) {
        return (permissions & permission.bit()) == permission.bit();
    }

    public void add(Permission permission) {
        permissions |= permission.bit();
    }

    public void remove(Permission permission) {
        permissions &= ~permission.bit();
    }

    public void resetPermissions() {
        permissions = 0;
    }

    // ---------- 普通访问器 ----------
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isDefaultRole() { return defaultRole; }
    public void setDefaultRole(boolean v) { this.defaultRole = v; }
    public int getPermissionBits() { return permissions; }

    @Override
    public String toString() { return "Role[" + name + "]"; }
}
