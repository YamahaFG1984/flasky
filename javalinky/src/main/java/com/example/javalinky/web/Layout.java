package com.example.javalinky.web;

import com.example.javalinky.model.Permission;
import com.example.javalinky.model.User;
import java.util.List;

public record Layout(
    String title,
    String csrfToken,
    List<Flash> flashes,
    User currentUser          // 未登录时是 null
) {
    public boolean isAuthenticated() {
        return currentUser != null;
    }

    /** 当前用户是否有某权限。未登录一律返回 false —— 相当于原书的 AnonymousUser */
    public boolean can(Permission permission) {
        return currentUser != null && currentUser.can(permission);
    }

    public boolean isAdmin() {
        return can(Permission.ADMIN);
    }

    /** 当前用户是否就是这个人（判断“编辑自己的资料”用） */
    public boolean is(User other) {
        return currentUser != null && other != null
            && currentUser.getId().equals(other.getId());
    }
}
