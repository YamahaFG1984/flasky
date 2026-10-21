package com.example.javalinky.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Follow 的复合主键。
 * 规则：字段名必须和 Follow 里标了 @Id 的字段同名，
 *      类型是对应实体的主键类型（User 的主键是 Long）。
 * 另外必须有公开的无参构造器，并正确实现 equals/hashCode。
 */
public class FollowId implements Serializable {

    private Long follower;
    private Long followed;

    public FollowId() {}

    public FollowId(Long follower, Long followed) {
        this.follower = follower;
        this.followed = followed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FollowId other)) return false;
        return Objects.equals(follower, other.follower)
            && Objects.equals(followed, other.followed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(follower, followed);
    }
}
