package com.dienmay.catalog.service;

import com.dienmay.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class FileStorageServiceTest {
    @TempDir Path directory;
    private FileStorageService storage;
    private final byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};

    @BeforeEach
    void setUp() { storage = new FileStorageService(directory.toString()); }

    @Test
    void store_invalidExtension_rejectsFile() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "test.svg", "image/svg+xml", png)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_mismatchedContentType_rejectsFile() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "test.png", "image/jpeg", png)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_fakeImageContent_rejectsFile() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "test.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void store_fileAboveTwoMegabytes_rejectsFile() throws Exception {
        byte[] bytes = new byte[(int) FileStorageService.MAX_BYTES + 1];
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", "large.png", "image/png", bytes)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("2MB");
        try (var files = Files.list(directory)) { assertThat(files.count()).isZero(); }
    }

    @Test
    void store_originalName_usesUuidInsteadOfOriginalName() throws Exception {
        String url = storage.store(new MockMultipartFile("image", "my-secret-original.png", "image/png", png));
        assertThat(url).matches("/uploads/[0-9a-f-]{36}\\.png");
        assertThat(url).doesNotContain("my-secret-original");
        assertThat(Files.readAllBytes(directory.resolve(url.substring(9)))).isEqualTo(png);
    }

    @Test
    void store_traversalOriginalName_staysInsideUploadDirectory() throws Exception {
        String url = storage.store(new MockMultipartFile("image", "../x.png", "image/png", png));
        Path saved = directory.resolve(url.substring(9)).normalize();
        assertThat(saved.getParent()).isEqualTo(directory);
        assertThat(saved).exists();
        assertThat(url).doesNotContain("..", "x.png");
    }

    @Test
    void delete_traversalUrl_doesNotTouchOutsideFile() throws Exception {
        Path outside = Files.createTempFile(directory.getParent(), "outside-", ".png");
        try {
            storage.delete("/uploads/../" + outside.getFileName());
            assertThat(outside).exists();
        } finally { Files.deleteIfExists(outside); }
    }

    @Test
    void delete_storedImage_removesFile() {
        String url = storage.store(new MockMultipartFile("image", "old.png", "image/png", png));
        storage.delete(url);
        assertThat(directory.resolve(url.substring(9))).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"jpg", "jpeg", "png", "webp"})
    void store_supportedFormats_acceptsFile(String extension) {
        String mime = extension.equals("png") ? "image/png" : extension.equals("webp") ? "image/webp" : "image/jpeg";
        byte[] bytes = extension.equals("png") ? png : extension.equals("webp")
                ? new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'}
                : new byte[]{(byte) 255, (byte) 216, (byte) 255};
        String url = storage.store(new MockMultipartFile("image", "original." + extension, mime, bytes));
        assertThat(directory.resolve(url.substring(9))).exists();
    }

    @Test
    void store_emptyFile_rejectsFile() {
        assertThatThrownBy(() -> storage.store(new MockMultipartFile("image", new byte[0])))
                .isInstanceOf(BusinessException.class);
    }
}
