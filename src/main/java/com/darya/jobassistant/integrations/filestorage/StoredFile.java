package com.darya.jobassistant.integrations.filestorage;

/**
 * Sprint 10 Step 4: provider-neutral confirmation of one {@link FileStoragePort#store} call -
 * deliberately carries no local filesystem {@code Path}/{@code File} or any other implementation-
 * specific handle, so the application/domain layer never depends on where or how a provider
 * physically keeps bytes.
 *
 * @param storageKey the application-generated key the content was stored under (echoed back, not
 *     reinterpreted)
 * @param sizeBytes the stored content's exact size
 * @param sha256Checksum the stored content's SHA-256 checksum, lowercase hex
 * @param alreadyExisted {@code true} if this call found equivalent content already stored under
 *     {@code storageKey} (a same-content idempotent store - see {@link FileStoragePort#store});
 *     {@code false} if this call actually wrote new content
 */
public record StoredFile(String storageKey, long sizeBytes, String sha256Checksum, boolean alreadyExisted) {

    public StoredFile {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Stored file storageKey must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Stored file sizeBytes must be positive");
        }
        if (sha256Checksum == null || sha256Checksum.isBlank()) {
            throw new IllegalArgumentException("Stored file sha256Checksum must not be blank");
        }
    }
}
