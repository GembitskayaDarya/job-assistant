package com.darya.jobassistant.integrations.filestorage.local;

import com.darya.jobassistant.integrations.filestorage.FileStorageContentConflictException;
import com.darya.jobassistant.integrations.filestorage.FileStorageException;
import com.darya.jobassistant.integrations.filestorage.FileStorageNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.integrations.filestorage.StoredFile;
import com.darya.jobassistant.integrations.filestorage.StoredFileContent;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Sprint 10 Step 4: the only layer that knows application-material file storage is currently
 * backed by the local filesystem - implements {@link FileStoragePort}. A future {@code
 * S3FileStorageAdapter} would implement the same port without any change to {@code
 * RenderApplicationMaterialsUseCase}.
 *
 * <h2>Path confinement</h2>
 *
 * Every {@code storageKey} is application-generated and deterministic (never derived from user-
 * controlled text) - but this adapter still enforces confinement in depth: an upfront reject of any
 * key containing a {@code ..} segment or that parses as absolute, <em>and</em> a resolve-then-
 * normalize-then-{@code startsWith(root)} check on the actual resolved path before any filesystem
 * operation. Either check alone would already stop the traversal patterns this adapter has to
 * defend against; both together cover a caller that hands this adapter a key it should never have
 * produced.
 *
 * <h2>Atomicity and idempotency</h2>
 *
 * {@link #store} never writes directly to the final path: content is written to a temporary file in
 * the same target directory first, then moved into place with {@link
 * StandardCopyOption#ATOMIC_MOVE} where the filesystem supports it (falling back to a plain,
 * still-same-filesystem move otherwise) - a reader can therefore never observe a partially-written
 * file at the final storage key. If the key already holds content, its checksum is read and
 * compared: an identical checksum makes this call a safe, idempotent no-op ({@link
 * StoredFile#alreadyExisted()} {@code true}); a different checksum throws {@link
 * FileStorageContentConflictException} rather than silently overwriting a different file. This
 * codebase's storage keys are deterministic per immutable generation (see {@code
 * RenderApplicationMaterialsUseCase}), so two concurrent stores for the same key are always storing
 * the same bytes - a benign race with no locking required.
 */
@Component
public class LocalFileStorageAdapter implements FileStoragePort {

    private final Path rootDirectory;

    public LocalFileStorageAdapter(LocalFileStorageProperties properties) {
        this.rootDirectory = Path.of(properties.rootDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(String storageKey, byte[] content, String contentType) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("File storage content must not be empty");
        }
        Path target = resolveWithinRoot(storageKey);
        String newChecksum = sha256Hex(content);

        if (Files.exists(target)) {
            String existingChecksum = readExistingChecksum(target, storageKey);
            if (existingChecksum.equals(newChecksum)) {
                return new StoredFile(storageKey, content.length, newChecksum, true);
            }
            throw new FileStorageContentConflictException(storageKey);
        }

        writeAtomically(target, content, storageKey);
        return new StoredFile(storageKey, content.length, newChecksum, false);
    }

    @Override
    public StoredFileContent load(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        if (!Files.exists(target)) {
            throw new FileStorageNotFoundException(storageKey);
        }
        byte[] content;
        try {
            content = Files.readAllBytes(target);
        } catch (IOException e) {
            throw new FileStorageException("Failed to read content at storage key '" + storageKey + "'", e);
        }
        return new StoredFileContent(storageKey, content, content.length, sha256Hex(content));
    }

    /**
     * Upfront rejection of an obviously unsafe key, before any path resolution is even attempted -
     * see class javadoc for why this is deliberately redundant with {@link #requireWithinRoot}.
     */
    private Path resolveWithinRoot(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        Path parsed = Path.of(storageKey);
        if (parsed.isAbsolute()) {
            throw new FileStorageException("Storage key must be relative, but was absolute: " + storageKey);
        }
        for (Path segment : parsed) {
            if (segment.toString().equals("..")) {
                throw new FileStorageException("Storage key must not contain '..' segments: " + storageKey);
            }
        }
        Path resolved = rootDirectory.resolve(storageKey).normalize();
        return requireWithinRoot(resolved, storageKey);
    }

    private Path requireWithinRoot(Path resolved, String storageKey) {
        if (!resolved.startsWith(rootDirectory)) {
            throw new FileStorageException("Storage key resolves outside the configured storage root: " + storageKey);
        }
        return resolved;
    }

    private String readExistingChecksum(Path target, String storageKey) {
        try {
            return sha256Hex(Files.readAllBytes(target));
        } catch (IOException e) {
            throw new FileStorageException("Failed to read existing content at storage key '" + storageKey + "'", e);
        }
    }

    private void writeAtomically(Path target, byte[] content, String storageKey) {
        Path parentDirectory = target.getParent();
        try {
            Files.createDirectories(parentDirectory);
        } catch (IOException e) {
            throw new FileStorageException("Failed to create storage directory for key '" + storageKey + "'", e);
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile(parentDirectory, ".tmp-" + target.getFileName(), null);
        } catch (IOException e) {
            throw new FileStorageException("Failed to create a temporary file for key '" + storageKey + "'", e);
        }

        try {
            Files.write(tempFile, content);
            moveIntoPlace(tempFile, target);
        } catch (IOException e) {
            throw new FileStorageException("Failed to store content at storage key '" + storageKey + "'", e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // The temp file is a no-op leftover at this point (either the move already
                // consumed it, or the write/move itself already failed and was reported above) -
                // never masks the real outcome of this call.
            }
        }
    }

    /** Same-filesystem move (temp file and target always share {@code rootDirectory}), atomic where the platform supports it. */
    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in this JVM", e);
        }
    }
}
