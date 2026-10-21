package com.example.javalinky.util;

import java.util.Arrays;

/**
 * 按「魔数」识别图片类型（第 20 章）。
 *
 * 为什么不看扩展名和 Content-Type：这两个都是<strong>客户端说了算</strong>的。
 * 把 shell.jsp 改名叫 avatar.png 再伪造一个 image/png 的 Content-Type，
 * 只看这两样的服务器就收下了。文件开头那几个字节是真正的内容，骗不了。
 */
public enum ImageType {

    PNG ("png",  "image/png",  new int[]{0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
    JPEG("jpg",  "image/jpeg", new int[]{0xFF, 0xD8, 0xFF}),
    GIF ("gif",  "image/gif",  new int[]{'G', 'I', 'F', '8'}),
    WEBP("webp", "image/webp", new int[]{'R', 'I', 'F', 'F'});   // 另需第 8..11 字节是 WEBP

    /** 够判断上面所有格式了 */
    public static final int MAGIC_BYTES = 12;

    private final String extension;
    private final String contentType;
    private final int[] magic;

    ImageType(String extension, String contentType, int[] magic) {
        this.extension = extension;
        this.contentType = contentType;
        this.magic = magic;
    }

    public String extension()   { return extension; }
    public String contentType() { return contentType; }

    /**
     * 认不出来就返回 null —— 调用方据此拒绝。
     * 注意是「白名单」：不在列表里的一律不收，而不是「黑名单」挡掉已知的坏格式。
     */
    public static ImageType detect(byte[] head) {
        for (ImageType type : values()) {
            if (matches(head, type.magic) && type.extraCheck(head)) return type;
        }
        return null;
    }

    /** 按扩展名反查，用于把存好的文件再发出去时设置 Content-Type */
    public static ImageType byExtension(String ext) {
        return Arrays.stream(values())
            .filter(t -> t.extension.equalsIgnoreCase(ext))
            .findFirst().orElse(null);
    }

    private boolean extraCheck(byte[] head) {
        // RIFF 容器不只有 webp，还要看第 8..11 字节
        if (this != WEBP) return true;
        return head.length >= 12
            && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private static boolean matches(byte[] head, int[] magic) {
        if (head.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if ((head[i] & 0xFF) != magic[i]) return false;
        }
        return true;
    }
}
