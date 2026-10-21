package com.example.javalinky.util;

import com.example.javalinky.model.User;

/**
 * 一个用户的头像来源（第 20 章）。
 *
 * 之前只有 Gravatar，所以页面模型里直接放一个 avatarHash 字符串就够了。
 * 现在多了「上传的文件」这条路，页面模型改成携带这个 record ——
 * 模板只管调 url(size)，不用关心头像到底从哪来。
 */
public record Avatar(String file, String hash) {

    public static Avatar of(User user) {
        return new Avatar(user.getAvatarFile(), user.avatarHash());
    }

    public boolean uploaded() {
        return file != null && !file.isBlank();
    }

    /**
     * size 对上传的头像不起作用（我们原样存、原样发）。
     * 真要多尺寸就得在上传时生成几份缩略图 —— 本章不做，练习里有。
     */
    public String url(int size) {
        return uploaded() ? "/avatars/" + file : Gravatar.url(hash, size);
    }

    /** 高分屏用的 2x。上传的头像没有 2x，返回同一个地址即可 */
    public String url2x(int size) {
        return uploaded() ? url(size) : Gravatar.url(hash, size * 2);
    }
}
