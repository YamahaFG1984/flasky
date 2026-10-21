package com.example.javalinky.model;

import io.javalin.security.RouteRole;
import java.util.EnumSet;
import java.util.Set;

/**
 * 一项权限。
 * 实现 Javalin 的 RouteRole 接口，这样可以直接写在路由声明上 —— 见本章后面。
 */
public enum Permission implements RouteRole {

    FOLLOW(1),      // 关注别人
    COMMENT(2),     // 发表评论
    WRITE(4),       // 发布文章
    MODERATE(8),    // 协管别人的评论
    ADMIN(16);      // 管理员

    private final int bit;

    Permission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    // ---------- 整数 ↔ 集合 ----------

    /** 把数据库里的整数解析成权限集合 */
    public static EnumSet<Permission> fromBits(int bits) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (Permission p : values()) {
            if ((bits & p.bit) == p.bit) set.add(p);
        }
        return set;
    }

    /** 把权限集合打包成一个整数 */
    public static int toBits(Set<Permission> permissions) {
        int bits = 0;
        for (Permission p : permissions) bits |= p.bit;
        return bits;
    }
}
