package com.example.javalinky.model;

import jakarta.persistence.*;

import java.time.Instant;

/** 用户在某个第三方服务商那里的身份（第 22 章） */
@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 32)
    private String provider;

    /** 服务商那边的用户 id。<b>不是</b>邮箱，也不是用户名 —— 那两个都会变 */
    @Column(name = "provider_user_id", nullable = false, length = 128)
    private String providerUserId;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt = Instant.now();

    protected OAuthIdentity() {}

    public OAuthIdentity(User user, String provider, String providerUserId) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public Long    getId()             { return id; }
    public User    getUser()           { return user; }
    public String  getProvider()       { return provider; }
    public String  getProviderUserId() { return providerUserId; }
    public Instant getLinkedAt()       { return linkedAt; }
}
