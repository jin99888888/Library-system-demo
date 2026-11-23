package com.example.library.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图书封面的本地文件存储。
 * <p>文件保存在配置项 {@code library.cover-dir} 指向的目录（默认 {@code data/covers}），
 * 页面通过 {@code /covers/**} 访问。</p>
 */
@Service
public class CoverStorage {

    /** 封面的访问路径前缀，同时也是静态资源映射的路径 */
    public static final String URL_PREFIX = "/covers/";

    private static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp");

    private final Path root;

    public CoverStorage(@Value("${library.cover-dir:data/covers}") String directory) throws IOException {
        this.root = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /** 封面文件所在目录的绝对路径。 */
    public Path getRoot() {
        return root;
    }

    /**
     * 保存上传的封面图片。
     *
     * @return 可访问的路径（形如 {@code /covers/xxx.png}）；未上传文件时返回 {@code null}
     */
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("封面图片不能超过 2 MB");
        }
        String extension = EXTENSIONS.get(contentType(file));
        if (extension == null) {
            throw new BusinessException("封面只支持 PNG、JPG、GIF、WEBP 格式的图片");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("封面保存失败，请稍后重试");
        }
        return URL_PREFIX + name;
    }

    /** 删除已保存的封面；空路径、非本地上传路径与可疑路径会被忽略。 */
    public void delete(String coverPath) {
        if (coverPath == null || !coverPath.startsWith(URL_PREFIX)) {
            return;
        }
        String name = coverPath.substring(URL_PREFIX.length());
        if (name.isBlank() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(name));
        } catch (IOException ignored) {
            // 删除失败不影响业务，残留文件会在清理封面目录时处理
        }
    }

    private static String contentType(MultipartFile file) {
        String type = file.getContentType();
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }
}
