package com.example.javalinky.web;

import io.javalin.security.RouteRole;

public enum Access implements RouteRole {
    /** 任何人都能访问（不写角色时的默认行为，写出来是为了可读性） */
    PUBLIC,
    /** 必须登录，但不要求任何具体权限 */
    AUTHENTICATED
}
