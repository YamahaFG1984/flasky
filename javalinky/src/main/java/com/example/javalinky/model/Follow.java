package com.example.javalinky.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow {

    /** 关注的发起者 */
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** 被关注的人 */
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "followed_id", nullable = false)
    private User followed;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    protected Follow() {}

    public Follow(User follower, User followed) {
        this.follower = follower;
        this.followed = followed;
    }

    public User getFollower() { return follower; }
    public User getFollowed() { return followed; }
    public Instant getTimestamp() { return timestamp; }
}
