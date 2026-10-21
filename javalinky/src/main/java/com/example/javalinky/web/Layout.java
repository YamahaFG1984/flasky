package com.example.javalinky.web;

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
}
