package com.example.library.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class CoverStorageTest {

    @TempDir
    Path tempDir;

    private CoverStorage storage() throws IOException {
        return new CoverStorage(tempDir.toString());
    }

    private byte[] pngBytes() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
    }

    @Test
    @DisplayName("保存 png 封面后返回可访问路径，文件落在配置目录")
    void savesImageAndReportsUrl() throws IOException {
        CoverStorage storage = storage();
        MockMultipartFile file = new MockMultipartFile("cover", "a.png", "image/png", pngBytes());

        String url = storage.save(file);

        assertThat(url).startsWith(CoverStorage.URL_PREFIX).endsWith(".png");
        Path saved = storage.getRoot().resolve(url.substring(CoverStorage.URL_PREFIX.length()));
        assertThat(saved).exists();
        assertThat(Files.readAllBytes(saved)).isEqualTo(pngBytes());

        storage.delete(url);
        assertThat(saved).doesNotExist();
    }

    @Test
    @DisplayName("未上传封面时返回 null")
    void emptyFileReturnsNull() throws IOException {
        assertThat(storage().save(null)).isNull();
        assertThat(storage().save(new MockMultipartFile("cover", new byte[0]))).isNull();
    }

    @Test
    @DisplayName("拒绝非图片类型的封面")
    void rejectsUnsupportedType() throws IOException {
        MockMultipartFile file = new MockMultipartFile("cover", "a.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> storage().save(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("封面只支持");
    }

    @Test
    @DisplayName("拒绝超过 2 MB 的封面")
    void rejectsTooLargeFile() throws IOException {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("cover", "big.png", "image/png", big);

        assertThatThrownBy(() -> storage().save(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 MB");
    }

    @Test
    @DisplayName("删除时忽略空路径与可疑路径")
    void deleteIgnoresUnsafePaths() throws IOException {
        CoverStorage storage = storage();

        storage.delete(null);
        storage.delete("/etc/passwd");
        storage.delete(CoverStorage.URL_PREFIX + "../evil.png");
        storage.delete(CoverStorage.URL_PREFIX);

        assertThat(Files.list(storage.getRoot())).isEmpty();
    }
}
