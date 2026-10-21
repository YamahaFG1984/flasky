package com.example.javalinky.model;

import com.example.javalinky.util.Passwords;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "ix_users_email", columnList = "email", unique = true),
    @Index(name = "ix_users_username", columnList = "username", unique = true)
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, nullable = false)
    private String email;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(name = "password_hash", length = 128)
    private String passwordHash;

    @Column(nullable = false)
    private boolean confirmed = false;

    @Column(name = "member_since", nullable = false)
    private Instant memberSince = Instant.now();

    /** 关系的"多"这一侧：真正的外键列在这里 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    protected User() {}

    public User(String email, String username) {
        this.email = email;
        this.username = username;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }

    /** 设置密码。注意：参数是明文，但存进字段的是哈希 */
    public void setPassword(String plaintext) {
        this.passwordHash = Passwords.hash(plaintext);
    }

    /** 校验密码 */
    public boolean checkPassword(String plaintext) {
        return Passwords.verify(plaintext, this.passwordHash);
    }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean c) { this.confirmed = c; }
    public Instant getMemberSince() { return memberSince; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    /** 相当于原书的 user.can(permission) */
    public boolean can(Permission permission) {
        return role != null && role.has(permission);
    }

    public boolean isAdministrator() {
        return can(Permission.ADMIN);
    }

    @Override
    public String toString() {
        return "User[" + username + "]";
    }
}
