package com.darya.jobassistant.integrations.filestorage.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.integrations.filestorage.FileStorageContentConflictException;
import com.darya.jobassistant.integrations.filestorage.FileStorageException;
import com.darya.jobassistant.integrations.filestorage.FileStorageNotFoundException;
import com.darya.jobassistant.integrations.filestorage.StoredFile;
import com.darya.jobassistant.integrations.filestorage.StoredFileContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sprint 10 Step 4: proves {@link LocalFileStorageAdapter} against a real, isolated temporary
 * directory - never the application's real configured {@code application-materials} directory.
 */
class LocalFileStorageAdapterTest {

    @TempDir
    private Path tempRoot;

    private LocalFileStorageAdapter adapter() {
        return new LocalFileStorageAdapter(new LocalFileStorageProperties(tempRoot.toString()));
    }

    // ==================== 14. Writes to the configured root ====================

    @Test
    void store_writesFileBeneathConfiguredRoot() throws IOException {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);

        StoredFile result = adapter().store("vacancies/v1/generations/g1/renderer-v1/cv.pdf", content, "application/pdf");

        Path expected = tempRoot.resolve("vacancies/v1/generations/g1/renderer-v1/cv.pdf");
        assertThat(Files.exists(expected)).isTrue();
        assertThat(Files.readAllBytes(expected)).isEqualTo(content);
        assertThat(result.alreadyExisted()).isFalse();
        assertThat(result.sizeBytes()).isEqualTo(content.length);
    }

    // ==================== 15. Path traversal rejected ====================

    @Test
    void store_storageKeyWithDotDot_isRejected() {
        assertThatThrownBy(() -> adapter().store("../escape.pdf", "content".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void store_storageKeyEmbeddingDotDotSegment_isRejected() {
        assertThatThrownBy(() -> adapter().store("vacancies/../../etc/passwd", "content".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(FileStorageException.class);
    }

    @Test
    void store_absoluteStorageKey_isRejected() {
        assertThatThrownBy(() -> adapter().store("/etc/passwd", "content".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(FileStorageException.class);
    }

    // ==================== 16. Storage never exposes absolute paths through its port model ====================

    @Test
    void store_returnedStoredFile_echoesTheStorageKeyVerbatim_neverAnAbsolutePath() {
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";

        StoredFile result = adapter().store(storageKey, "content".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThat(result.storageKey()).isEqualTo(storageKey);
        assertThat(result.storageKey()).doesNotContain(tempRoot.toString());
    }

    // ==================== 17. Repeated same-content store is idempotent ====================

    @Test
    void store_sameContentTwice_isIdempotent() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";

        StoredFile first = adapter().store(storageKey, content, "application/pdf");
        StoredFile second = adapter().store(storageKey, content, "application/pdf");

        assertThat(first.alreadyExisted()).isFalse();
        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.sha256Checksum()).isEqualTo(first.sha256Checksum());
    }

    // ==================== 18. Different-content store to an existing key is rejected ====================

    @Test
    void store_differentContentAtSameKey_throwsContentConflict() {
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";
        adapter().store(storageKey, "first version".getBytes(StandardCharsets.UTF_8), "application/pdf");

        assertThatThrownBy(() -> adapter().store(storageKey, "different version".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(FileStorageContentConflictException.class);
    }

    // ==================== 19. Partial/temp files are not treated as completed artifacts ====================

    @Test
    void store_leavesNoTemporaryFilesBehindInTheTargetDirectory() throws IOException {
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";

        adapter().store(storageKey, "content".getBytes(StandardCharsets.UTF_8), "application/pdf");

        Path directory = tempRoot.resolve("vacancies/v1/generations/g1/renderer-v1");
        try (Stream<Path> entries = Files.list(directory)) {
            List<String> fileNames = entries.map(p -> p.getFileName().toString()).toList();
            assertThat(fileNames).containsExactly("cv.pdf");
        }
    }

    // ==================== 20. SHA-256 stored metadata matches actual file bytes ====================

    @Test
    void store_checksumMatchesActualPersistedBytes() throws Exception {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";

        StoredFile result = adapter().store(storageKey, content, "application/pdf");

        Path stored = tempRoot.resolve(storageKey);
        byte[] actualBytes = Files.readAllBytes(stored);
        String actualChecksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actualBytes));
        assertThat(result.sha256Checksum()).isEqualTo(actualChecksum);
    }

    @Test
    void store_emptyContent_isRejected() {
        assertThatThrownBy(() -> adapter().store("vacancies/v1/generations/g1/renderer-v1/cv.pdf", new byte[0], "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== load(): round-trip byte identity ====================

    @Test
    void load_afterStore_returnsExactSameBytes() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";
        adapter().store(storageKey, content, "application/pdf");

        StoredFileContent loaded = adapter().load(storageKey);

        assertThat(loaded.content()).isEqualTo(content);
        assertThat(loaded.storageKey()).isEqualTo(storageKey);
    }

    // ==================== load(): checksum/size consistency with store() ====================

    @Test
    void load_checksumAndSizeMatchWhatStoreReported() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";
        StoredFile stored = adapter().store(storageKey, content, "application/pdf");

        StoredFileContent loaded = adapter().load(storageKey);

        assertThat(loaded.sha256Checksum()).isEqualTo(stored.sha256Checksum());
        assertThat(loaded.sizeBytes()).isEqualTo(stored.sizeBytes());
        assertThat(loaded.sizeBytes()).isEqualTo(loaded.content().length);
    }

    // ==================== load(): already-persisted artifact retrievable without re-rendering ====================

    @Test
    void load_previouslyPersistedArtifact_isRetrievableByANewAdapterInstance() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";
        adapter().store(storageKey, content, "application/pdf");

        StoredFileContent loaded = adapter().load(storageKey);

        assertThat(loaded.content()).isEqualTo(content);
    }

    // ==================== load(): path traversal rejected for reads ====================

    @Test
    void load_storageKeyWithDotDot_isRejected() {
        assertThatThrownBy(() -> adapter().load("../escape.pdf"))
                .isInstanceOf(FileStorageException.class)
                .isNotInstanceOf(FileStorageNotFoundException.class);
    }

    @Test
    void load_storageKeyEmbeddingDotDotSegment_isRejected() {
        assertThatThrownBy(() -> adapter().load("vacancies/../../etc/passwd"))
                .isInstanceOf(FileStorageException.class)
                .isNotInstanceOf(FileStorageNotFoundException.class);
    }

    @Test
    void load_absoluteStorageKey_isRejected() {
        assertThatThrownBy(() -> adapter().load("/etc/passwd"))
                .isInstanceOf(FileStorageException.class)
                .isNotInstanceOf(FileStorageNotFoundException.class);
    }

    // ==================== load(): missing content fails safely ====================

    @Test
    void load_missingStorageKey_throwsFileStorageNotFoundException() {
        assertThatThrownBy(() -> adapter().load("vacancies/v1/generations/g1/renderer-v1/does-not-exist.pdf"))
                .isInstanceOf(FileStorageNotFoundException.class);
    }

    // ==================== load(): no absolute path leaks ====================

    @Test
    void load_returnedContent_neverLeaksTheAbsoluteStorageRoot() {
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String storageKey = "vacancies/v1/generations/g1/renderer-v1/cv.pdf";
        adapter().store(storageKey, content, "application/pdf");

        StoredFileContent loaded = adapter().load(storageKey);

        assertThat(loaded.storageKey()).isEqualTo(storageKey);
        assertThat(loaded.storageKey()).doesNotContain(tempRoot.toString());
    }
}
