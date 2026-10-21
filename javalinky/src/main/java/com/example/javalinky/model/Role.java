package com.example.javalinky.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

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

    @Column(nullable = false)
    private int permissions;

    /** 关系的"一"这一侧。mappedBy 指向 User 里那个字段的名字 */
    @OneToMany(mappedBy = "role", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();

    // JPA 要求有一个无参构造器（可以是 protected）
    protected Role() {}

    public Role(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isDefaultRole() { return defaultRole; }
    public void setDefaultRole(boolean v) { this.defaultRole = v; }
    public int getPermissions() { return permissions; }
    public void setPermissions(int p) { this.permissions = p; }
    public List<User> getUsers() { return users; }

    @Override
    public String toString() {
        return "Role[" + name + "]";
    }
}
