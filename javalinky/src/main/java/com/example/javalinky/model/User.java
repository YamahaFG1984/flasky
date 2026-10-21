package com.example.javalinky.model;

import com.example.javalinky.util.Gravatar;
import com.example.javalinky.util.Passwords;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Column(length = 64)
    private String name;            // 真名，可以不填

    @Column(length = 64)
    private String location;

    @Column(name = "about_me", columnDefinition = "text")
    private String aboutMe;

    @Column(name = "last_seen")
    private Instant lastSeen = Instant.now();

    /** Gravatar 的邮箱哈希。缓存起来，避免每次渲染都算一遍 */
    @Column(name = "avatar_hash", length = 32)
    private String avatarHash;

    /** 关系的"多"这一侧：真正的外键列在这里 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    /** 我关注了谁（我是 follower） */
    @OneToMany(mappedBy = "follower", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Follow> following = new ArrayList<>();

    /** 谁关注了我（我是 followed） */
    @OneToMany(mappedBy = "followed", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Follow> followers = new ArrayList<>();

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

    public List<Follow> getFollowing() { return following; }
    public List<Follow> getFollowers() { return followers; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getAboutMe() { return aboutMe; }
    public void setAboutMe(String aboutMe) { this.aboutMe = aboutMe; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }

    /**
     * 取 Gravatar 哈希。没有缓存就算一次并存下来。
     * 第 8 章的“改邮箱”会把这个字段置 null，这里就会重算。
     */
    public String avatarHash() {
        if (avatarHash == null || avatarHash.isBlank()) {
            avatarHash = Gravatar.hash(email);
        }
        return avatarHash;
    }

    public void setAvatarHash(String hash) {
        this.avatarHash = hash;
    }

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
