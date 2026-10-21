package com.example.javalinky.service;

import com.example.javalinky.util.ImageType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 头像文件的存取（第 20 章）。
 *
 * 三条原则：
 *   1. 文件名由<strong>服务端生成</strong>，绝不用客户端给的
 *   2. 类型按<strong>文件内容</strong>判断，不信扩展名和 Content-Type
 *   3. 读取时再校验一次路径，确保落在目录内（纵深防御）
 */
public final class AvatarStore {

    /** 上传大小上限。头像不需要更大了，而且这是第一道防线 */
    public static final long MAX_BYTES = 2 * 1024 * 1024;   // 2 MB

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path root;

    public AvatarStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** 上传失败的原因。分得细一点，好给用户一句人话 */
    public enum Rejection {
        EMPTY("请选择一个文件"),
        TOO_LARGE("图片不能超过 2 MB"),
        NOT_AN_IMAGE("只支持 PNG、JPEG、GIF、WebP 格式的图片");

        public final String message;
        Rejection(String message) { this.message = message; }
    }

    /** 存成功返回文件名（如 "a3f91b02….png"），失败返回被拒的原因 */
    public sealed interface Result {
        record Stored(String filename) implements Result {}
        record Rejected(Rejection reason) implements Result {}
    }

    public Result store(InputStream in, long declaredSize) {
        if (declaredSize <= 0) return new Result.Rejected(Rejection.EMPTY);
        if (declaredSize > MAX_BYTES) return new Result.Rejected(Rejection.TOO_LARGE);

        try {
            // 先落到临时文件：既能看头几个字节判类型，也能在真正写入前校验大小。
            // 注意不要先 readAllBytes() —— 那等于让任何人都能用一个大文件把堆撑爆。
            Files.createDirectories(root);
            Path temp = Files.createTempFile(root, "upload-", ".part");
            try {
                long written = Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);

                // declaredSize 来自客户端，不能信。这里是真实落盘的字节数。
                if (written == 0)          return new Result.Rejected(Rejection.EMPTY);
                if (written > MAX_BYTES)   return new Result.Rejected(Rejection.TOO_LARGE);

                ImageType type = ImageType.detect(readHead(temp));
                if (type == null) return new Result.Rejected(Rejection.NOT_AN_IMAGE);

                String filename = randomName() + "." + type.extension();
                Files.move(temp, root.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
                return new Result.Stored(filename);
            } finally {
                Files.deleteIfExists(temp);    // move 成功的话这里是空操作
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存头像失败", e);
        }
    }

    /**
     * 按文件名取出路径。
     * 这里<strong>再</strong>校验一次 —— 就算数据库里的值被人改成了
     * "../../etc/passwd"，也出不了这个目录。
     */
    public Optional<Path> find(String filename) {
        if (filename == null || filename.isBlank()) return Optional.empty();

        Path candidate = root.resolve(filename).normalize();
        if (!candidate.startsWith(root)) return Optional.empty();   // ← 目录穿越在这里被挡住
        if (!Files.isRegularFile(candidate)) return Optional.empty();
        return Optional.of(candidate);
    }

    public void delete(String filename) {
        find(filename).ifPresent(p -> {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                throw new UncheckedIOException("删除头像失败", e);
            }
        });
    }

    private static byte[] readHead(Path file) throws IOException {
        byte[] head = new byte[ImageType.MAGIC_BYTES];
        try (InputStream in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            return read == head.length ? head : java.util.Arrays.copyOf(head, Math.max(read, 0));
        }
    }

    private static String randomName() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
