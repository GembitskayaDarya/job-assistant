package com.darya.jobassistant.integrations.filestorage;

/**
 * Sprint 10 Step 4: provider-neutral file-storage boundary. The application layer must never call
 * {@code java.nio.file.Files} (or any other storage-provider API) directly - only this port. The
 * initial implementation, {@code LocalFileStorageAdapter}, stores under a configured local root
 * directory; a future {@code S3FileStorageAdapter} could implement this same contract without any
 * application-orchestration change.
 *
 * <p>{@code storageKey} is always application-generated from stable ids (see {@code
 * RenderApplicationMaterialsUseCase}'s storage-key strategy) - never derived from user-controlled
 * company/title/file names - and is deliberately opaque to callers: nothing outside a specific
 * adapter implementation may assume a particular physical layout from it.
 */
public interface FileStoragePort {

    /**
     * Idempotently stores {@code content} under {@code storageKey}. If nothing is currently stored
     * there, writes it and returns {@link StoredFile#alreadyExisted()} {@code false}. If content is
     * already stored there with the exact same SHA-256 checksum, no write happens and {@link
     * StoredFile#alreadyExisted()} is {@code true} - this call is then a safe no-op, not an error.
     * If content already stored there has a <em>different</em> checksum, throws {@link
     * FileStorageContentConflictException} rather than silently overwriting it.
     *
     * @throws FileStorageContentConflictException if {@code storageKey} already holds different content
     * @throws FileStorageException if the content cannot be stored for any other reason (e.g. an
     *     I/O failure, or {@code storageKey} attempting to escape the configured storage root)
     */
    StoredFile store(String storageKey, byte[] content, String contentType);

    /**
     * Loads the exact bytes previously stored under {@code storageKey} - the read counterpart of
     * {@link #store}, added so a caller (e.g. a future Telegram document-delivery flow) can obtain
     * an already-rendered artifact's content without knowing anything about how or where a provider
     * physically keeps it. Applies the exact same path-confinement checks as {@link #store}.
     *
     * @throws FileStorageNotFoundException if nothing is stored at {@code storageKey}
     * @throws FileStorageException if the content cannot be read for any other reason (e.g. an I/O
     *     failure, or {@code storageKey} attempting to escape the configured storage root)
     */
    StoredFileContent load(String storageKey);
}
