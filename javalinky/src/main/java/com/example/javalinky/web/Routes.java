package com.example.javalinky.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 全站 URL 的唯一来源。相当于 Flask 的 url_for。 */
public final class Routes {

    public static final String INDEX = "/";
    public static final String LOGIN = "/auth/login";
    public static final String LOGOUT = "/auth/logout";
    public static final String REGISTER = "/auth/register";
    public static final String EDIT_PROFILE = "/edit-profile";
    public static final String CHANGE_PASSWORD = "/auth/change-password";
    public static final String CHANGE_EMAIL = "/auth/change-email";
    public static final String RESET_PASSWORD = "/auth/reset";
    public static final String UNCONFIRMED = "/auth/unconfirmed";

    public static String user(String username) {
        return "/user/" + enc(username);
    }

    public static String post(long id) {
        return "/post/" + id;
    }

    public static String post(long id, int page) {
        return "/post/" + id + "?page=" + page;
    }

    public static final String MODERATE = "/moderate";

    public static String comments(long postId) {
        return "/post/" + postId + "/comments";
    }

    public static String enableComment(long id) {
        return "/moderate/enable/" + id;
    }

    public static String disableComment(long id) {
        return "/moderate/disable/" + id;
    }

    public static String editPost(long id) {
        return "/edit/" + id;
    }

    public static String adminEditProfile(long userId) {
        return "/admin/edit-profile/" + userId;
    }

    public static String follow(String username) {
        return "/follow/" + enc(username);
    }

    public static String unfollow(String username) {
        return "/unfollow/" + enc(username);
    }

    public static String followers(String username) {
        return "/user/" + enc(username) + "/followers";
    }

    public static String following(String username) {
        return "/user/" + enc(username) + "/following";
    }

    /** 绝对 URL：邮件里的链接必须用这个（第 6 章） */
    public static String absolute(String base, String path) {
        return base + path;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private Routes() {}
}
