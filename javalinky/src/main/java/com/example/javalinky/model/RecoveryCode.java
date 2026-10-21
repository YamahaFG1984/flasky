package com.example.javalinky.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 两步验证的恢复码（第 22 章）。
 *
 * 手机丢了、重装了、换了设备 —— 没有恢复码的话用户就永远进不来了，
 * 只能走人工客服。<b>任何两步验证方案都必须有一条自助的后路。</b>
 *
 * 存的是哈希，不是明文：数据库泄露时它们等同于密码。
 */
@Entity
@Table(name = "recovery_codes")
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    /** 用过就记下时间，一码只能用一次 */
    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {}

    public RecoveryCode(User user, String codeHash) {
        this.user = user;
        this.codeHash = codeHash;
    }

    public Long    getId()       { return id; }
    public User    getUser()     { return user; }
    public String  getCodeHash() { return codeHash; }
    public Instant getUsedAt()   { return usedAt; }
    public boolean isUsed()      { return usedAt != null; }

    public void markUsed() { this.usedAt = Instant.now(); }
}
